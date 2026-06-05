(function () {
  const API_BASE = '/api/v1/accounting/vouchers';
  const VENDOR_SEARCH_API = '/api/v1/inventory/vendors/search';

  window.voucherQueryService = {
    // 1. 전표 요약 정보 로드 (KPI)
    async fetchSummary() {
      const baseUrl = window.ddukSession?.getApiBaseUrl?.() || '';
      return window.ddukApi.get(`${API_BASE}/summary`);
    },

    // 2. 전표 목록 조회 (유형, 필터 포함)
    async fetchVouchers(type, filters = {}) {
      const params = new URLSearchParams();
      if (type) params.set('type', type);
      if (filters.status) params.set('status', filters.status);
      if (filters.keyword) params.set('keyword', filters.keyword.trim());
      if (filters.startDate) params.set('startDate', filters.startDate);
      if (filters.endDate) params.set('endDate', filters.endDate);
      
      return window.ddukApi.get(`${API_BASE}?${params.toString()}`);
    },

    // 3. 전표 단건 상세 조회 (신규 추가!)
    async fetchVoucherDetail(id) {
      if (!id) throw new Error('전표 ID가 필요합니다.');
      return window.ddukApi.get(`${API_BASE}/${id}`);
    },

    // 4. 계정과목 검색
    async searchAccounts(keyword, type, cashOnly = false) {
      const params = new URLSearchParams();
      if (keyword) params.set('keyword', keyword.trim());
      if (type) params.set('type', type);
      if (cashOnly) params.set('cashOnly', 'true');
      
      return window.ddukApi.get(`${API_BASE}/accounts/search?${params.toString()}`);
    },

    // 5. 거래처 검색
    async searchVendors(keyword) {
      const params = new URLSearchParams();
      if (keyword) params.set('name', keyword.trim());
      
      return window.ddukApi.get(`${VENDOR_SEARCH_API}?${params.toString()}`);
    }
  };
})();
