import axios from 'axios';
import { tokenUtils } from '../utils/token';
import authService from '../services/auth.service';
import { globalLogoutHandler } from '../utils/globalLogout';

let isRefreshing = false;
let requestQueue = [];

const processQueue = (error, newToken = null) => {
    requestQueue.forEach(prom => {
        if (error) {
            prom.reject(error);
        } else {
            prom.resolve(newToken);
        }
    });
    requestQueue = [];
};

export const responseInterceptor = (response) => {
    return response;
};

export const responseErrorInterceptor = async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
        originalRequest._retry = true;

        if (isRefreshing) {
            return new Promise((resolve, reject) => {
                requestQueue.push({
                    resolve: (newToken) => {
                        originalRequest.headers.Authorization = `Bearer ${newToken}`;
                        resolve(axios(originalRequest));
                    },
                    reject: (err) => reject(err)
                });
            });
        }

        isRefreshing = true;

        try {
            const currentToken = tokenUtils.getAccessToken();

            if (!currentToken) {
                globalLogoutHandler.triggerLogout('session_expired');
                return Promise.reject(error);
            }

            const response = await authService.refreshToken({ token: currentToken });

            const { data } = response.data;
            console.log('Token refreshed successfully:', data);
            const newToken = data.token;

            if (!newToken) {
                throw new Error('No token received from refresh endpoint');
            }

            tokenUtils.setTokens(newToken);

            processQueue(null, newToken);

            originalRequest.headers.Authorization = `Bearer ${newToken}`;
            return axios(originalRequest);

        } catch (refreshError) {
            console.error('Token refresh failed:', refreshError);
            console.error('Details:', {
                status: refreshError.response?.status,
                message: refreshError.response?.data?.message
            });

            processQueue(refreshError, null);

            let reason = 'token_refresh_failed';

            if (refreshError.response?.status === 401) {
                const msg = refreshError.response?.data?.message || '';
                if (msg.includes('hết hạn')) reason = 'session_expired';
                else if (msg.includes('vô hiệu hóa')) reason = 'token_invalidated';
                else if (msg.includes('không hợp lệ')) reason = 'unauthorized';
                else reason = 'session_expired';
            } else if (refreshError.response?.status === 404) {
                reason = 'unauthorized';
            }

            globalLogoutHandler.triggerLogout(reason);
            return Promise.reject(refreshError);

        } finally {
            isRefreshing = false;
        }
    }

    if (error.response?.status === 401 && originalRequest._retry) {
        globalLogoutHandler.triggerLogout('session_expired');
    }

    return Promise.reject(error);
};
