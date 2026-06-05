/**
 * High-Grade Payroll Calculation Engine (Explainable & Snapshotted)
 */
import { PAYROLL_RULES, applyRounding } from './payroll-rules.js';

export class PayrollEngine {
    /**
     * Calculate individual record with detailed trace
     */
    static calculate(employee, input = {}) {
        const steps = [];
        const baseSalary = employee.baseSalary || 0;
        steps.push({ step: 'BASE_SALARY', input: { base: baseSalary }, output: baseSalary });

        const overtimePay = this.calculateOvertime(baseSalary, input.overtimeHours || 0, steps);
        const bonus = input.bonus || 0;
        const allowance = input.allowance || 0;
        steps.push({ step: 'EXTRA_PAY', input: { bonus, allowance }, output: bonus + allowance });

        const grossPay = baseSalary + overtimePay + bonus + allowance;
        steps.push({ step: 'GROSS_PAY', output: grossPay });

        // Deductions
        const pension = this.calculateDeduction(grossPay, PAYROLL_RULES.DEDUCTIONS.NATIONAL_PENSION, steps, 'PENSION');
        const health = this.calculateDeduction(grossPay, PAYROLL_RULES.DEDUCTIONS.HEALTH_INSURANCE, steps, 'HEALTH');
        
        const ltRate = PAYROLL_RULES.DEDUCTIONS.LONGTERM_CARE.rate;
        const longterm = applyRounding(health * ltRate, PAYROLL_RULES.ROUNDING.TAX);
        steps.push({ step: 'LONGTERM_CARE', input: { health, rate: ltRate }, output: longterm });

        const employment = this.calculateDeduction(grossPay, PAYROLL_RULES.DEDUCTIONS.EMPLOYMENT_INSURANCE, steps, 'EMPLOYMENT');

        // Income Tax
        const incomeTax = this.calculateIncomeTax(grossPay, steps);
        const localIncomeTax = applyRounding(incomeTax * 0.1, PAYROLL_RULES.ROUNDING.TAX);
        steps.push({ step: 'LOCAL_INCOME_TAX', input: { incomeTax, rate: 0.1 }, output: localIncomeTax });

        const totalDeductions = pension + health + longterm + employment + incomeTax + localIncomeTax;
        const netPay = grossPay - totalDeductions;
        steps.push({ step: 'NET_PAY', output: netPay });

        return {
            amounts: {
                baseSalary, overtimePay, bonus, allowance,
                grossPay, pension, health, longterm, employment,
                incomeTax, localIncomeTax, totalDeductions, netPay
            },
            trace: steps,
            snapshot: {
                rules: JSON.parse(JSON.stringify(PAYROLL_RULES)), // Deep freeze rules
                employeeContract: { baseSalary: employee.baseSalary, position: employee.position },
                inputs: { ...input }
            }
        };
    }

    static calculateOvertime(baseSalary, hours, steps) {
        const hourlyRate = baseSalary / 209;
        const pay = applyRounding(hourlyRate * 1.5 * hours, PAYROLL_RULES.ROUNDING.DEFAULT);
        steps.push({ step: 'OVERTIME', input: { baseSalary, hours, hourlyRate, multiplier: 1.5 }, output: pay });
        return pay;
    }

    static calculateDeduction(amount, rule, steps, stepName) {
        let deduction = amount * rule.rate;
        if (rule.maxAmount && deduction > rule.maxAmount) {
            deduction = rule.maxAmount;
        }
        const pay = applyRounding(deduction, PAYROLL_RULES.ROUNDING.TAX);
        steps.push({ step: stepName, input: { gross: amount, rate: rule.rate, max: rule.maxAmount }, output: pay });
        return pay;
    }

    static calculateIncomeTax(grossPay, steps) {
        const annualSalary = grossPay * 12;
        const bracket = PAYROLL_RULES.INCOME_TAX_BRACKETS.find(b => annualSalary <= b.limit) 
                     || PAYROLL_RULES.INCOME_TAX_BRACKETS[PAYROLL_RULES.INCOME_TAX_BRACKETS.length - 1];
        
        const annualTax = (annualSalary * bracket.rate) - bracket.deduction;
        const pay = applyRounding(annualTax / 12, PAYROLL_RULES.ROUNDING.TAX);
        steps.push({ step: 'INCOME_TAX', input: { annualSalary, rate: bracket.rate, ded: bracket.deduction }, output: pay });
        return pay;
    }
}
