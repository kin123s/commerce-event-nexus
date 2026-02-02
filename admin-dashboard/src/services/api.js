import axios from 'axios';

const ORDER_SERVICE_URL = process.env.REACT_APP_ORDER_SERVICE_URL || 'http://localhost:8080';
const PAYMENT_SERVICE_URL = process.env.REACT_APP_PAYMENT_SERVICE_URL || 'http://localhost:8081';

// Order Service API
export const orderAPI = {
  createOrder: (orderData) => 
    axios.post(`${ORDER_SERVICE_URL}/api/orders`, orderData),
  
  getAllOrders: () => 
    axios.get(`${ORDER_SERVICE_URL}/api/orders`),
  
  getOrderById: (id) => 
    axios.get(`${ORDER_SERVICE_URL}/api/orders/${id}`),
  
  getOrderByNumber: (orderNumber) => 
    axios.get(`${ORDER_SERVICE_URL}/api/orders/number/${orderNumber}`)
};

// Payment Service API
export const paymentAPI = {
  getAllPayments: () => 
    axios.get(`${PAYMENT_SERVICE_URL}/api/payments`),
  
  getPaymentById: (id) => 
    axios.get(`${PAYMENT_SERVICE_URL}/api/payments/${id}`),
  
  getPaymentByOrderNumber: (orderNumber) => 
    axios.get(`${PAYMENT_SERVICE_URL}/api/payments/order/${orderNumber}`)
};
