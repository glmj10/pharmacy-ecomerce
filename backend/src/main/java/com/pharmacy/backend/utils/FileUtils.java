package com.pharmacy.backend.utils;

import org.springframework.beans.factory.annotation.Value;

public class FileUtils {

    @Value("${file.upload.max-size}")
    private static long MAX_FILE_SIZE;

    public static boolean checkSize(long size) {
        return size <= MAX_FILE_SIZE * 1024 * 1024;
    }
}
