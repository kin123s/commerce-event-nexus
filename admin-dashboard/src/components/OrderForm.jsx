import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Grid,
  Box,
  Alert
} from '@mui/material';
import { orderAPI } from '../services/api';

const OrderForm = ({ open, onClose, onSuccess }) => {
  const [formData, setFormData] = useState({
    productName: '',
    quantity: 1,
    price: '',
    customerName: '',
    customerEmail: ''
  });
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: name === 'quantity' ? parseInt(value) || 1 : value
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      await orderAPI.createOrder(formData);
      setFormData({
        productName: '',
        quantity: 1,
        price: '',
        customerName: '',
        customerEmail: ''
      });
      onSuccess && onSuccess();
      onClose();
    } catch (err) {
      setError(err.response?.data?.message || '주문 생성에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ fontWeight: 'bold' }}>새 주문 생성</DialogTitle>
      <form onSubmit={handleSubmit}>
        <DialogContent>
          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}
          
          <Grid container spacing={2}>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="제품명"
                name="productName"
                value={formData.productName}
                onChange={handleChange}
                required
              />
            </Grid>
            
            <Grid item xs={6}>
              <TextField
                fullWidth
                type="number"
                label="수량"
                name="quantity"
                value={formData.quantity}
                onChange={handleChange}
                inputProps={{ min: 1 }}
                required
              />
            </Grid>
            
            <Grid item xs={6}>
              <TextField
                fullWidth
                type="number"
                label="가격"
                name="price"
                value={formData.price}
                onChange={handleChange}
                inputProps={{ min: 0, step: "0.01" }}
                required
              />
            </Grid>
            
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="고객명"
                name="customerName"
                value={formData.customerName}
                onChange={handleChange}
                required
              />
            </Grid>
            
            <Grid item xs={12}>
              <TextField
                fullWidth
                type="email"
                label="이메일"
                name="customerEmail"
                value={formData.customerEmail}
                onChange={handleChange}
                required
              />
            </Grid>
          </Grid>
        </DialogContent>
        
        <DialogActions>
          <Button onClick={onClose} disabled={loading}>
            취소
          </Button>
          <Button 
            type="submit" 
            variant="contained" 
            disabled={loading}
          >
            {loading ? '처리 중...' : '주문 생성'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default OrderForm;
