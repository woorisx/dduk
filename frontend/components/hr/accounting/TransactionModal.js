/**
 * Transaction Modal Component
 */

import { TRANSACTION_TYPE, TRANSACTION_STATUS, PAYMENT_METHOD } from '../../services/hr/accounting-constants.js';
import { calculateVat, calculateTotalAmount } from '../../services/hr/accounting-utils.js';

export class TransactionModal {
    constructor(options = {}) {
        this.container = options.container;
        this.onSave = options.onSave || (() => {});
        this.transaction = null;
    }

    open(transaction = null) {
        this.transaction = transaction;
        this.render();
        document.body.style.overflow = 'hidden';
        document.querySelector('.erp_modal_overlay').classList.add('is_active');
    }

    close() {
        document.body.style.overflow = '';
        document.querySelector('.erp_modal_overlay').classList.remove('is_active');
    }

    render() {
        const isEdit = !!this.transaction;
        this.container.innerHTML = `
            <div class="erp_modal_overlay">
                <div class="erp_modal">
                    <div class="erp_modal_header">
                        <h2>${isEdit ? '거래 수정' : '신규 거래 등록'}</h2>
                        <button type="button" class="btn_close" style="background:none; border:none; cursor:pointer; font-size:1.5rem;">&times;</button>
                    </div>
                    <div class="erp_modal_body">
                        <form id="transaction_form" style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
                            <div class="form_group" style="grid-column: span 1;">
                                <label style="display:block; font-size:0.875rem; margin-bottom:4px;">거래 일자 *</label>
                                <input type="date" name="transactionDate" class="erp_input" required value="${this.transaction?.transactionDate || new Date().toISOString().split('T')[0]}">
                            </div>
                            <div class="form_group">
                                <label style="display:block; font-size:0.875rem; margin-bottom:4px;">구분 *</label>
                                <select name="type" class="erp_select" required>
                                    <option value="${TRANSACTION_TYPE.PURCHASE}" ${this.transaction?.type === TRANSACTION_TYPE.PURCHASE ? 'selected' : ''}>매입</option>
                                    <option value="${TRANSACTION_TYPE.SALES}" ${this.transaction?.type === TRANSACTION_TYPE.SALES ? 'selected' : ''}>매출</option>
                                </select>
                            </div>
                            <div class="form_group" style="grid-column: span 2;">
                                <label style="display:block; font-size:0.875rem; margin-bottom:4px;">거래처명 *</label>
                                <input type="text" name="vendorName" class="erp_input" required placeholder="거래처명을 입력하세요" value="${this.transaction?.vendorName || ''}">
                            </div>
                            <div class="form_group">
                                <label style="display:block; font-size:0.875rem; margin-bottom:4px;">공급가액 *</label>
                                <input type="number" name="supplyPrice" class="erp_input" required placeholder="0" value="${this.transaction?.supplyPrice || 0}">
                            </div>
                            <div class="form_group">
                                <label style="display:block; font-size:0.875rem; margin-bottom:4px;">부가세</label>
                                <input type="number" name="vat" class="erp_input" placeholder="0" value="${this.transaction?.vat || 0}">
                            </div>
                            <div class="form_group">
                                <label style="display:block; font-size:0.875rem; margin-bottom:4px;">총액</label>
                                <input type="number" name="totalAmount" class="erp_input" readonly style="background:#f8fafc;" value="${this.transaction?.totalAmount || 0}">
                            </div>
                            <div class="form_group">
                                <label style="display:block; font-size:0.875rem; margin-bottom:4px;">결제수단</label>
                                <select name="paymentMethod" class="erp_select">
                                    <option value="${PAYMENT_METHOD.TRANSFER}" ${this.transaction?.paymentMethod === PAYMENT_METHOD.TRANSFER ? 'selected' : ''}>계좌이체</option>
                                    <option value="${PAYMENT_METHOD.CARD}" ${this.transaction?.paymentMethod === PAYMENT_METHOD.CARD ? 'selected' : ''}>카드</option>
                                    <option value="${PAYMENT_METHOD.CASH}" ${this.transaction?.paymentMethod === PAYMENT_METHOD.CASH ? 'selected' : ''}>현금</option>
                                </select>
                            </div>
                            <div class="form_group">
                                <label style="display:block; font-size:0.875rem; margin-bottom:4px;">상태</label>
                                <select name="status" class="erp_select">
                                    <option value="${TRANSACTION_STATUS.PENDING}" ${this.transaction?.status === TRANSACTION_STATUS.PENDING ? 'selected' : ''}>대기</option>
                                    <option value="${TRANSACTION_STATUS.COMPLETED}" ${this.transaction?.status === TRANSACTION_STATUS.COMPLETED ? 'selected' : ''}>완료</option>
                                    <option value="${TRANSACTION_STATUS.CANCELED}" ${this.transaction?.status === TRANSACTION_STATUS.CANCELED ? 'selected' : ''}>취소</option>
                                </select>
                            </div>
                            <div class="form_group" style="grid-column: span 2;">
                                <label style="display:block; font-size:0.875rem; margin-bottom:4px;">비고</label>
                                <textarea name="note" class="erp_input" style="height:80px; padding-top:8px;">${this.transaction?.note || ''}</textarea>
                            </div>
                        </form>
                    </div>
                    <div class="erp_modal_footer">
                        <button type="button" class="erp_button erp_button_secondary btn_cancel">취소</button>
                        <button type="button" class="erp_button erp_button_primary btn_save">저장</button>
                    </div>
                </div>
            </div>
        `;

        this.initEvents();
    }

    initEvents() {
        const form = document.getElementById('transaction_form');
        const supplyPriceInput = form.querySelector('input[name="supplyPrice"]');
        const vatInput = form.querySelector('input[name="vat"]');
        const totalAmountInput = form.querySelector('input[name="totalAmount"]');

        const updateAmounts = () => {
            const supplyPrice = parseInt(supplyPriceInput.value) || 0;
            const vat = parseInt(vatInput.value) || calculateVat(supplyPrice);
            if (event.target === supplyPriceInput) {
                vatInput.value = vat;
            }
            totalAmountInput.value = calculateTotalAmount(supplyPrice, parseInt(vatInput.value) || 0);
        };

        supplyPriceInput.addEventListener('input', updateAmounts);
        vatInput.addEventListener('input', updateAmounts);

        this.container.querySelector('.btn_close').addEventListener('click', () => this.close());
        this.container.querySelector('.btn_cancel').addEventListener('click', () => this.close());
        this.container.querySelector('.btn_save').addEventListener('click', () => this.handleSave());
    }

    handleSave() {
        const form = document.getElementById('transaction_form');
        if (!form.checkValidity()) {
            form.reportValidity();
            return;
        }

        const formData = new FormData(form);
        const data = Object.fromEntries(formData.entries());
        
        // Convert numbers
        data.supplyPrice = parseInt(data.supplyPrice);
        data.vat = parseInt(data.vat);
        data.totalAmount = parseInt(data.totalAmount);
        
        if (this.transaction) {
            data.id = this.transaction.id;
        }

        this.onSave(data);
    }
}
