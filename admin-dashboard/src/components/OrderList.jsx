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
import { orderAPI } from '../services/api';

const OrderList = () => {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchOrders = async () => {
    try {
      setLoading(true);
      const response = await orderAPI.getAllOrders();
      setOrders(response.data);
      setError(null);
    } catch (err) {
      setError('주문 목록을 불러오는데 실패했습니다.');
      console.error('Error fetching orders:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders();
    // 5초마다 자동 새로고침
    const interval = setInterval(fetchOrders, 5000);
    return () => clearInterval(interval);
  }, []);

  const getStatusColor = (status) => {
    const colors = {
      PENDING: 'warning',
      CONFIRMED: 'info',
      PAID: 'success',
      SHIPPED: 'primary',
      DELIVERED: 'success',
      CANCELLED: 'error'
    };
    return colors[status] || 'default';
  };

  if (loading && orders.length === 0) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="h5" gutterBottom sx={{ mb: 3, fontWeight: 'bold' }}>
        📦 주문 목록
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
              <TableCell><strong>주문번호</strong></TableCell>
              <TableCell><strong>제품명</strong></TableCell>
              <TableCell align="center"><strong>수량</strong></TableCell>
              <TableCell align="right"><strong>총 금액</strong></TableCell>
              <TableCell><strong>고객명</strong></TableCell>
              <TableCell><strong>이메일</strong></TableCell>
              <TableCell align="center"><strong>상태</strong></TableCell>
              <TableCell><strong>주문일시</strong></TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {orders.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8} align="center">
                  <Typography color="textSecondary">주문 내역이 없습니다.</Typography>
                </TableCell>
              </TableRow>
            ) : (
              orders.map((order) => (
                <TableRow key={order.id} hover>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 'bold' }}>
                      {order.orderNumber}
                    </Typography>
                  </TableCell>
                  <TableCell>{order.productName}</TableCell>
                  <TableCell align="center">{order.quantity}</TableCell>
                  <TableCell align="right">
                    <strong>₩{order.totalAmount.toLocaleString()}</strong>
                  </TableCell>
                  <TableCell>{order.customerName}</TableCell>
                  <TableCell>{order.customerEmail}</TableCell>
                  <TableCell align="center">
                    <Chip
                      label={order.status}
                      color={getStatusColor(order.status)}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="textSecondary">
                      {new Date(order.createdAt).toLocaleString('ko-KR')}
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

export default OrderList;
