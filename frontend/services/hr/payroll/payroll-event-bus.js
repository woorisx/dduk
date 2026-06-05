/**
 * Payroll Domain Event Bus
 */

export class PayrollEventBus {
    constructor() {
        this.subscribers = new Map();
    }

    /**
     * Subscribe to a domain event
     */
    subscribe(eventType, callback) {
        if (!this.subscribers.has(eventType)) {
            this.subscribers.set(eventType, []);
        }
        this.subscribers.get(eventType).push(callback);
    }

    /**
     * Publish a domain event
     */
    publish(eventType, data) {
        const callbacks = this.subscribers.get(eventType) || [];
        callbacks.forEach(callback => {
            try {
                callback(data);
            } catch (err) {
                console.error(`[EventBus] Error in subscriber for ${eventType}:`, err);
            }
        });
    }
}

export const payrollEventBus = new PayrollEventBus();

// Pre-defined Event Types
export const PAYROLL_EVENTS = {
    CALCULATED: 'PAYROLL_CALCULATED',
    APPROVED: 'PAYROLL_APPROVED',
    POSTED: 'PAYROLL_POSTED',
    POSTING_FAILED: 'PAYROLL_POSTING_FAILED',
    RECONCILIATION_FAILED: 'RECONCILIATION_FAILED',
    CLOSED: 'PAYROLL_CLOSED'
};
