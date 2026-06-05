/**
 * DDUK ERP 재고관리 전용 API 서비스
 * - 모든 fetch는 window.ddukApi(apiClient.js)를 통해 처리
 * - JWT, base URL, 401/에러 처리는 apiClient.js에 위임
 * - 직접 fetch 호출 금지
 */
const InventoryService = {
    getDashboardStats: () =>
        window.ddukApi.get('/api/v1/inventory/dashboard/stats'),

    getStocks: (params = {}) => {
        const qs = new URLSearchParams(
            Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== null && v !== undefined))
        ).toString();
        return window.ddukApi.get(`/api/v1/inventories${qs ? '?' + qs : ''}`);
    },

    getMovements: (params = {}) => {
        const qs = new URLSearchParams(
            Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== null && v !== undefined))
        ).toString();
        return window.ddukApi.get(`/api/v1/inventories/stock-movements${qs ? '?' + qs : ''}`);
    },

    transfer: (data) =>
        window.ddukApi.post('/api/v1/inventories/transfer', data),

    getReorderRecommendations: () =>
        window.ddukApi.get('/api/v1/inventories/reorder-recommendations'),

    getPurchaseRecommendations: () =>
        window.ddukApi.get('/api/v1/inventory/purchase-recommendations'),

    getWarehouses: () =>
        window.ddukApi.get('/api/v1/warehouses'),

    getTransfers: (params = {}) => {
        const qs = new URLSearchParams(
            Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== null && v !== undefined))
        ).toString();
        return window.ddukApi.get(`/api/v1/warehouse-transfers${qs ? '?' + qs : ''}`);
    },

    getTransferDetail: (id) =>
        window.ddukApi.get(`/api/v1/warehouse-transfers/${id}`),

    requestTransfer: (data) =>
        window.ddukApi.post('/api/v1/warehouse-transfers', data),

    approveTransfer: (id) =>
        window.ddukApi.post(`/api/v1/warehouse-transfers/${id}/approve`, {}),

    completeTransfer: (id) =>
        window.ddukApi.post(`/api/v1/warehouse-transfers/${id}/complete`, {}),

    cancelTransfer: (id, data = {}) =>
        window.ddukApi.post(`/api/v1/warehouse-transfers/${id}/cancel`, data)
};
