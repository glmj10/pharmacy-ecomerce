import React, { useState, useEffect } from 'react'
import {
  CCard,
  CCardBody,
  CCardHeader,
  CCol,
  CRow,
  CTable,
  CTableBody,
  CTableDataCell,
  CTableHead,
  CTableHeaderCell,
  CTableRow,
  CBadge,
  CDropdown,
  CDropdownItem,
  CDropdownMenu,
  CDropdownToggle,
  CPagination,
  CPaginationItem,
  CSpinner,
  CModal,
  CModalBody,
  CModalFooter,
  CModalHeader,
  CModalTitle,
  CFormSelect,
  CButton,
} from '@coreui/react'
import CIcon from '@coreui/icons-react'
import {
  cilOptions,
  cilInfo,
  cilCheckCircle,
  cilXCircle,
  cilClock,
} from '@coreui/icons'
import { contactService } from '../../../services'
import { useApiCall } from '../../../hooks/useApiCall'

const ContactList = () => {
  const [contacts, setContacts] = useState([])
  const [loading, setLoading] = useState(true)
  const [currentPage, setCurrentPage] = useState(1)
  const [totalPages, setTotalPages] = useState(0)
  const [statusFilter, setStatusFilter] = useState('all')
  const [statusModal, setStatusModal] = useState({
    visible: false,
    contact: null,
    newStatus: ''
  })
  const [detailModal, setDetailModal] = useState({ visible: false, contact: null })

  const { execute: callApi } = useApiCall()

  const CONTACT_STATUSES = {
    true: { label: 'Đã xử lý', color: 'success' },
    false: { label: 'Chưa xử lý', color: 'warning' },
  }

  const fetchContacts = async (page = 1, status = 'all') => {
    setLoading(true)
    try {
      const params = {
        pageIndex: page,
        pageSize: 10,
        ...(status !== 'all' && { status: status === 'true' }),
      }

      const response = await callApi(() => contactService.getContacts(params))
      if (response.success) {
        const contactsData = Array.isArray(response.data)
          ? response.data
          : response.data.content || response.data.data || []
        setContacts(contactsData)
        setTotalPages(response.data.totalPages || 1)
        setCurrentPage(page)
      } else {
        setContacts([])
      }
    } catch (error) {
      console.error('Error fetching contacts:', error)
      setContacts([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchContacts()
  }, [])

  const handleStatusFilter = (status) => {
    setStatusFilter(status)
    setCurrentPage(1)
    fetchContacts(1, status)
  }

  const handlePageChange = (page) => {
    fetchContacts(page, statusFilter)
  }

  const handleStatusChange = async () => {
    if (!statusModal.contact || !statusModal.newStatus) return

    try {
      const statusLabel = CONTACT_STATUSES[statusModal.newStatus]?.label || 'Không xác định'
      await callApi(() =>
        contactService.updateContactStatus(statusModal.contact.id, statusModal.newStatus),
        {
          successMessage: `Cập nhật trạng thái liên hệ thành công! Trạng thái mới: ${statusLabel}`,
          showSuccessNotification: true,
          onSuccess: () => {
            setStatusModal({ visible: false, contact: null, newStatus: '' })
            fetchContacts(currentPage, statusFilter)
          }
        }
      )
    } catch (error) {
      console.error('Error updating contact status:', error)
    }
  }

  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleDateString('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  const generatePaginationItems = () => {
    const items = []
    const maxVisiblePages = 5
    const halfVisible = Math.floor(maxVisiblePages / 2)

    let startPage = Math.max(1, currentPage - halfVisible)
    let endPage = Math.min(totalPages, currentPage + halfVisible)

    if (currentPage <= halfVisible) {
      endPage = Math.min(totalPages, maxVisiblePages)
    }
    if (currentPage > totalPages - halfVisible) {
      startPage = Math.max(1, totalPages - maxVisiblePages + 1)
    }

    if (startPage > 1) {
      items.push(
        <CPaginationItem
          key={1}
          onClick={() => handlePageChange(1)}
          style={{ cursor: 'pointer' }}
          title="Trang 1"
        >
          1
        </CPaginationItem>
      )

      if (startPage > 2) {
        items.push(
          <CPaginationItem key="start-ellipsis" disabled>
            ...
          </CPaginationItem>
        )
      }
    }

    for (let page = startPage; page <= endPage; page++) {
      items.push(
        <CPaginationItem
          key={page}
          active={currentPage === page}
          onClick={() => handlePageChange(page)}
          style={{ cursor: 'pointer' }}
          title={`Trang ${page}`}
        >
          {page}
        </CPaginationItem>
      )
    }

    if (endPage < totalPages) {
      if (endPage < totalPages - 1) {
        items.push(
          <CPaginationItem key="end-ellipsis" disabled>
            ...
          </CPaginationItem>
        )
      }

      items.push(
        <CPaginationItem
          key={totalPages}
          onClick={() => handlePageChange(totalPages)}
          style={{ cursor: 'pointer' }}
          title={`Trang ${totalPages}`}
        >
          {totalPages}
        </CPaginationItem>
      )
    }

    return items
  }

  return (
    <>
      <CRow>
        <CCol xs={12}>
          <CCard className="mb-4">
            <CCardHeader>
              <strong>Quản lý liên hệ</strong>
            </CCardHeader>
            <CCardBody>
              {/* Status Filter */}
              <CRow className="mb-3">
                <CCol md={4}>
                  <CFormSelect
                    value={statusFilter}
                    onChange={(e) => handleStatusFilter(e.target.value)}
                  >
                    <option value="all">Tất cả</option>
                    <option value="true">Đã xử lý</option>
                    <option value="false">Chưa xử lý</option>
                  </CFormSelect>
                </CCol>
              </CRow>

              {/* Table */}
              {loading ? (
                <div className="text-center py-4">
                  <CSpinner color="primary" />
                </div>
              ) : (
                <>
                  <CTable hover responsive>
                    <CTableHead>
                      <CTableRow>
                        <CTableHeaderCell scope="col">Thông tin liên hệ</CTableHeaderCell>
                        <CTableHeaderCell scope="col">Số điện thoại</CTableHeaderCell>
                        <CTableHeaderCell scope="col">Nội dung</CTableHeaderCell>
                        <CTableHeaderCell scope="col">Trạng thái</CTableHeaderCell>
                        <CTableHeaderCell scope="col">Ngày gửi</CTableHeaderCell>
                        <CTableHeaderCell scope="col">Thao tác</CTableHeaderCell>
                      </CTableRow>
                    </CTableHead>
                    <CTableBody>
                      {Array.isArray(contacts) && contacts.map((contact) => (
                        <CTableRow
                          key={contact.id}
                          style={{
                            opacity: contact.status === false ? 0.7 : 1,
                            backgroundColor: contact.status === false ? 'rgba(255, 193, 7, 0.1)' : 'transparent'
                          }}
                        >
                          <CTableDataCell>
                            <div>
                              <strong>{contact.fullName || contact.name}</strong>
                              <br />
                              <small className="text-muted">{contact.email}</small>
                              {contact.subject && (
                                <>
                                  <br />
                                  <small className="text-info">Chủ đề: {contact.subject}</small>
                                </>
                              )}
                            </div>
                          </CTableDataCell>
                          <CTableDataCell>
                            <span>{contact.phoneNumber || 'Không có'}</span>
                          </CTableDataCell>
                          <CTableDataCell>
                            <div style={{ maxWidth: '200px' }}>
                              {contact.content && contact.content.length > 100
                                ? `${contact.content.substring(0, 100)}...`
                                : contact.content
                              }
                            </div>
                          </CTableDataCell>
                          <CTableDataCell>
                            <CBadge color={CONTACT_STATUSES[contact.active]?.color || 'secondary'}>
                              {CONTACT_STATUSES[contact.active]?.label || 'Không xác định'}
                            </CBadge>
                          </CTableDataCell>
                          <CTableDataCell>
                            <small>{formatDate(contact.createdAt)}</small>
                          </CTableDataCell>
                          <CTableDataCell>
                            <CDropdown>
                              <CDropdownToggle color="ghost" caret={false}>
                                <CIcon icon={cilOptions} />
                              </CDropdownToggle>
                              <CDropdownMenu>
                                <CDropdownItem
                                  onClick={() => setDetailModal({ visible: true, contact })}
                                >
                                  <CIcon icon={cilInfo} className="me-2" />
                                  Chi tiết
                                </CDropdownItem>
                                <CDropdownItem
                                  onClick={() => setStatusModal({
                                    visible: true,
                                    contact,
                                    newStatus: contact.status
                                  })}
                                >
                                  <CIcon icon={cilCheckCircle} className="me-2" />
                                  Cập nhật trạng thái
                                </CDropdownItem>
                              </CDropdownMenu>
                            </CDropdown>
                          </CTableDataCell>
                        </CTableRow>
                      ))}
                    </CTableBody>
                  </CTable>

                  {/* Pagination */}
                  {totalPages > 1 && (
                    <CPagination align="center" className="mt-3">
                      <CPaginationItem
                        disabled={currentPage === 1}
                        onClick={() => handlePageChange(currentPage - 1)}
                      >
                        Trước
                      </CPaginationItem>
                      {generatePaginationItems()}
                      <CPaginationItem
                        disabled={currentPage === totalPages}
                        onClick={() => handlePageChange(currentPage + 1)}
                      >
                        Sau
                      </CPaginationItem>
                    </CPagination>
                  )}
                </>
              )}
            </CCardBody>
          </CCard>
        </CCol>
      </CRow>

      {/* Status Update Modal */}
      <CModal
        visible={statusModal.visible}
        onClose={() => setStatusModal({ visible: false, contact: null, newStatus: '' })}
      >
        <CModalHeader>
          <CModalTitle>Cập nhật trạng thái liên hệ</CModalTitle>
        </CModalHeader>
        <CModalBody>
          <p>Liên hệ từ: <strong>{statusModal.contact?.fullName || statusModal.contact?.name}</strong></p>
          <p>Email: <strong>{statusModal.contact?.email}</strong></p>
          <p>Số điện thoại: <strong>{statusModal.contact?.phoneNumber || 'Không có'}</strong></p>
          <p>Chủ đề: <strong>{statusModal.contact?.subject}</strong></p>
          <CFormSelect
            value={statusModal.newStatus}
            onChange={(e) => setStatusModal(prev => ({ ...prev, newStatus: e.target.value }))}
          >
            {Object.entries(CONTACT_STATUSES).map(([key, value]) => (
              <option key={key} value={key}>{value.label}</option>
            ))}
          </CFormSelect>
        </CModalBody>
        <CModalFooter>
          <CButton
            color="secondary"
            onClick={() => setStatusModal({ visible: false, contact: null, newStatus: '' })}
          >
            Hủy
          </CButton>
          <CButton color="primary" onClick={handleStatusChange}>
            Cập nhật
          </CButton>
        </CModalFooter>
      </CModal>

      {/* Contact Detail Modal */}
      <CModal
        size="lg"
        visible={detailModal.visible}
        onClose={() => setDetailModal({ visible: false, contact: null })}
      >
        <CModalHeader>
          <CModalTitle>Chi tiết liên hệ</CModalTitle>
        </CModalHeader>
        <CModalBody>
          {detailModal.contact && (
            <>
              <CRow className="mb-3">
                <CCol md={6}>
                  <strong>Tên:</strong> {detailModal.contact.fullName}
                </CCol>
                <CCol md={6}>
                  <strong>Email:</strong> {detailModal.contact.email}
                </CCol>
              </CRow>

              <CRow className="mb-3">
                <CCol md={6}>
                  <strong>Số điện thoại: </strong> {detailModal.contact.phoneNumber || 'Không có'}
                </CCol>
              </CRow>
              <CRow className="mb-3">
                <CCol md={6}>
                  <strong>Địa chỉ </strong> {detailModal.contact.address}
                </CCol>
              </CRow>

              <CRow className="mb-3">
                <CCol md={12}>
                  <strong>Nội dung:</strong>
                  <div className="mt-2 p-3 bg-light rounded">
                    {detailModal.contact.content}
                  </div>
                </CCol>
              </CRow>

              <CRow className="mb-3">
                <CCol md={12}>
                  <strong>Ngày gửi:</strong> {formatDate(detailModal.contact.createdAt)}
                </CCol>
              </CRow>
            </>
          )}
        </CModalBody>
        <CModalFooter>
          <CButton
            color="secondary"
            onClick={() => setDetailModal({ visible: false, contact: null })}
          >
            Đóng
          </CButton>
          <CButton
            color="primary"
            onClick={() => {
              setDetailModal({ visible: false, contact: null })
              setStatusModal({
                visible: true,
                contact: detailModal.contact,
                newStatus: detailModal.contact?.active || 'PENDING'
              })
            }}
          >
            Cập nhật trạng thái
          </CButton>
        </CModalFooter>
      </CModal>
    </>
  )
}

export default ContactList
