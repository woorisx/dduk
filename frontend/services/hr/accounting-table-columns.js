/**
 * Accounting Table Column Configuration
 */

import { formatCurrency, formatDate } from './accounting-utils.js';
import { TRANSACTION_TYPE_LABEL, TRANSACTION_STATUS_LABEL } from './accounting-constants.js';

export const transactionColumns = [
    {
        key: "transactionDate",
        label: "날짜",
        sortable: true,
        width: "120px",
        render: (val) => formatDate(val)
    },
    {
        key: "type",
        label: "구분",
        sortable: true,
        width: "80px",
        render: (val) => TRANSACTION_TYPE_LABEL[val]
    },
    {
        key: "vendorName",
        label: "거래처명",
        sortable: true,
        render: (val) => val
    },
    {
        key: "supplyPrice",
        label: "공급가액",
        sortable: true,
        align: "right",
        width: "140px",
        render: (val) => formatCurrency(val)
    },
    {
        key: "vat",
        label: "부가세",
        sortable: true,
        align: "right",
        width: "120px",
        render: (val) => formatCurrency(val)
    },
    {
        key: "totalAmount",
        label: "총액",
        sortable: true,
        align: "right",
        width: "140px",
        render: (val) => formatCurrency(val)
    },
    {
        key: "status",
        label: "상태",
        sortable: true,
        width: "100px",
        render: (val) => TRANSACTION_STATUS_LABEL[val]
    },
    {
        key: "note",
        label: "비고",
        render: (val) => val || "-"
    }
];
