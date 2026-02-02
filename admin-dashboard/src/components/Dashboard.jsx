import React, { useState } from 'react';
import {
  Container,
  Box,
  Typography,
  Tabs,
  Tab,
  Paper,
  Button,
  AppBar,
  Toolbar
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import OrderList from './OrderList';
import PaymentList from './PaymentList';
import OrderForm from './OrderForm';

const Dashboard = () => {
  const [activeTab, setActiveTab] = useState(0);
  const [orderFormOpen, setOrderFormOpen] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  const handleTabChange = (event, newValue) => {
    setActiveTab(newValue);
  };

  const handleOrderSuccess = () => {
    setRefreshKey(prev => prev + 1);
  };

  return (
    <Box sx={{ minHeight: '100vh', backgroundColor: '#f5f5f5' }}>
      <AppBar position="static">
        <Toolbar>
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            🚀 주문-결제 관리 시스템 (Event-Driven Architecture)
          </Typography>
        </Toolbar>
      </AppBar>

      <Container maxWidth="xl" sx={{ mt: 4, mb: 4 }}>
        <Paper elevation={2} sx={{ mb: 3 }}>
          <Box sx={{ borderBottom: 1, borderColor: 'divider', display: 'flex', justifyContent: 'space-between', alignItems: 'center', px: 2 }}>
            <Tabs value={activeTab} onChange={handleTabChange}>
              <Tab label="주문 관리" />
              <Tab label="결제 관리" />
            </Tabs>
            {activeTab === 0 && (
              <Button
                variant="contained"
                startIcon={<AddIcon />}
                onClick={() => setOrderFormOpen(true)}
              >
                새 주문 생성
              </Button>
            )}
          </Box>
        </Paper>

        <Box sx={{ mt: 3 }}>
          {activeTab === 0 && <OrderList key={refreshKey} />}
          {activeTab === 1 && <PaymentList key={refreshKey} />}
        </Box>
      </Container>

      <OrderForm
        open={orderFormOpen}
        onClose={() => setOrderFormOpen(false)}
        onSuccess={handleOrderSuccess}
      />
    </Box>
  );
};

export default Dashboard;
