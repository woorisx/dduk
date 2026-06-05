(function () {
  const API_BASE = '/api/v1/accounting/vouchers';

  window.voucherCommandService = {
    // 1. 전표 생성
    async createVoucher(payload) {
      if (!payload) throw new Error('저장할 전표 데이터가 존재하지 않습니다.');
      return window.ddukApi.post(API_BASE, payload);
    },

    // 2. 전표 상태 업데이트 (임시저장 DRAFT -> 저장 REQUESTED -> 승인 APPROVED -> 게시 POSTED / 반려 CANCELLED)
    async updateVoucherStatus(id, status) {
      if (!id) throw new Error('대상 전표 ID가 누락되었습니다.');
      if (!status) throw new Error('변경할 상태 값이 필요합니다.');
      
      return window.ddukApi.patch(`${API_BASE}/${id}/status?status=${status}`);
    }
  };
})();
