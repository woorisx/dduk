/**
 * Transaction Filter Component (Inheriting BaseFilter)
 */
import { BaseFilter } from '../../common/BaseFilter.js';
import { TRANSACTION_TYPE, TRANSACTION_STATUS } from '../../../services/hr/accounting-constants.js';

export class TransactionFilter extends BaseFilter {
    constructor(options = {}) {
        const fields = [
            { id: 'startDate', label: '시작일', type: 'date' },
            { id: 'endDate', label: '종료일', type: 'date' },
            { id: 'type', label: '거래구분', type: 'select', options: [
                { value: '', label: '전체' },
                { value: TRANSACTION_TYPE.PURCHASE, label: '매입' },
                { value: TRANSACTION_TYPE.SALES, label: '매출' }
            ]},
            { id: 'status', label: '상태', type: 'select', options: [
                { value: '', label: '전체' },
                { value: TRANSACTION_STATUS.PENDING, label: '대기' },
                { value: TRANSACTION_STATUS.COMPLETED, label: '완료' },
                { value: TRANSACTION_STATUS.CANCELED, label: '취소' }
            ]},
            { id: 'keyword', label: '검색어', type: 'text', placeholder: '거래처명 입력' }
        ];

        super({
            ...options,
            fields
        });
    }
}
