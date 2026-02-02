import React, { useState, useEffect } from 'react';
import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  Typography,
  CircularProgress,
  Box,
  Alert
} from '@mui/material';
import { paymentAPI } from '../services/api';

const PaymentList = () => {
  const [payments, setPayments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchPayments = async () => {
    try {
      setLoading(true);
      const response = await paymentAPI.getAllPayments();
      setPayments(response.data);
      setError(null);
    } catch (err) {
      setError('결제 목록을 불러오는데 실패했습니다.');
      console.error('Error fetching payments:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPayments();
    // 5초마다 자동 새로고침
    const interval = setInterval(fetchPayments, 5000);
    return () => clearInterval(interval);
  }, []);

  const getStatusColor = (status) => {
    const colors = {
      PENDING: 'warning',
      PROCESSING: 'info',
      COMPLETED: 'success',
      FAILED: 'error',
      REFUNDED: 'default'
    };
    return colors[status] || 'default';
  };

  const getPaymentMethodLabel = (method) => {
    const labels = {
      CARD: '카드',
      BANK_TRANSFER: '계좌이체',
      MOBILE: '모바일'
    };
    return labels[method] || method;
  };

  if (loading && payments.length === 0) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="h5" gutterBottom sx={{ mb: 3, fontWeight: 'bold' }}>
        💳 결제 목록
      </Typography>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <TableContainer component={Paper} elevation={3}>
        <Table>
          <TableHead sx={{ backgroundColor: '#f5f5f5' }}>
            <TableRow>
              <TableCell><strong>결제번호</strong></TableCell>
              <TableCell><strong>주문번호</strong></TableCell>
              <TableCell align="right"><strong>금액</strong></TableCell>
              <TableCell><strong>고객명</strong></TableCell>
              <TableCell align="center"><strong>결제수단</strong></TableCell>
              <TableCell align="center"><strong>상태</strong></TableCell>
              <TableCell><strong>트랜잭션ID</strong></TableCell>
              <TableCell><strong>결제일시</strong></TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {payments.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8} align="center">
                  <Typography color="textSecondary">결제 내역이 없습니다.</Typography>
                </TableCell>
              </TableRow>
            ) : (
              payments.map((payment) => (
                <TableRow key={payment.id} hover>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 'bold' }}>
                      {payment.paymentNumber}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                      {payment.orderNumber}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <strong>₩{payment.amount.toLocaleString()}</strong>
                  </TableCell>
                  <TableCell>{payment.customerName}</TableCell>
                  <TableCell align="center">
                    <Chip
                      label={getPaymentMethodLabel(payment.paymentMethod)}
                      variant="outlined"
                      size="small"
                    />
                  </TableCell>
                  <TableCell align="center">
                    <Chip
                      label={payment.status}
                      color={getStatusColor(payment.status)}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="textSecondary" sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>
                      {payment.transactionId}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="textSecondary">
                      {new Date(payment.createdAt).toLocaleString('ko-KR')}
                    </Typography>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default PaymentList;
