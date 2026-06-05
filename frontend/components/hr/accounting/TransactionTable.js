/**
 * Transaction Table Component (Inheriting BaseTable)
 */
import { BaseTable } from '../../common/BaseTable.js';
import { TRANSACTION_COLUMNS } from '../../../services/hr/accounting-table-columns.js';

export class TransactionTable extends BaseTable {
    constructor(options = {}) {
        super({
            ...options,
            columns: TRANSACTION_COLUMNS
        });
        this.onEdit = options.onEdit || (() => {});
    }

    // Override or extend behaviors
    renderBody(table) {
        // We can reuse the parent's renderBody or customize it
        super.renderBody(table);
    }
}
