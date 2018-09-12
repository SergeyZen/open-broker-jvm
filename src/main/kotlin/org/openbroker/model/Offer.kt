package org.openbroker.model

import org.openbroker.requireMin
import java.math.BigDecimal
import java.math.MathContext

data class Offer(
    val effectiveInterestRate: String,
    val nominalInterestRate: String,
    val minOfferedCredit: Int,
    val offeredCredit: Int,
    val maxOfferedCredit: Int,
    val monthlyCost: Int? = null,
    val mustRefinance: Int,
    val arrangementFee: Int,
    val termFee: Int,
    val invoiceFee: Int,
    val termMonths: Int,
    val amortizationType: AmortizationType
) {
    init {
        val interestRateRegex = Regex("^[0-9]+(.[0-9]+)?$")

        require(effectiveInterestRate.matches(interestRateRegex)) {
            "Bad format of effective interest rate: '$effectiveInterestRate'"
        }

        require(nominalInterestRate.matches(interestRateRegex)) {
            "Bad format of nominal interest rate: '$nominalInterestRate'"
        }

        minOfferedCredit.requireMin(1, "minOfferedCredit")
        offeredCredit.requireMin(1, "offeredCredit")
        maxOfferedCredit.requireMin(1, "maxOfferedCredit")

        require(minOfferedCredit <= maxOfferedCredit) { "minOfferedCredit cannot be greater than maxOfferedCredit" }
        require(offeredCredit in minOfferedCredit..maxOfferedCredit) {
            "offeredCredit must be within the range of minOfferedCredit and maxOfferedCredit"
        }

        mustRefinance.requireMin(0, "mustRefinance")
        arrangementFee.requireMin(0, "arrangementFee")
        termFee.requireMin(0, "termFee")
        invoiceFee.requireMin(0, "invoiceFee")
        termMonths.requireMin(1, "termMonths")
    }
}

/**
 * Compute the total cost of a loan, given the
 * @param loanAmount the amount loaned
 * @param nominalAnnualInterestRate the nominal interest rate on annual basis
 * @param termFee any fee that is paid each month, not including amortization
 * and interest rate payments
 * @param termMonths for how many months the loan is payed off over
 *
 * @return the total cost of the loan
 */
fun totalCostOfLoan(
    loanAmount: Int,
    nominalAnnualInterestRate: Double,
    termFee: Int,
    termMonths: Int
): BigDecimal {
    val monthlyCost: BigDecimal = monthlyCostForRateAndAmortization(loanAmount, nominalAnnualInterestRate, termMonths)
    val monthlyTermCost = BigDecimal(termFee)
    return (monthlyCost + monthlyTermCost).multiply(BigDecimal(termMonths), MathContext.DECIMAL128)
}

fun monthlyCostForRateAndAmortization(
    loanAmount: Int,
    nominalAnnualInterestRate: Double,
    termMonths: Int
): BigDecimal {
    val monthlyRate: Double = nominalAnnualInterestRate / 12.0
    val fixedMonthlyPayment: Double = loanAmount * ((monthlyRate * Math.pow(1 + monthlyRate, termMonths.toDouble())) / (Math.pow(1 +
        monthlyRate, termMonths.toDouble()) - 1))
    return BigDecimal(fixedMonthlyPayment)
}

fun totalInterestRateCostsStraightLine(
    loanAmount: Int,
    nominalAnnualInterestRate: Double,
    termMonths: Int
): BigDecimal {
    val principal = BigDecimal(loanAmount)
    val monthlyRate = BigDecimal(nominalAnnualInterestRate/12.0)
    val rate = monthlyRate.divide(BigDecimal(2), MathContext.DECIMAL128)
    val x = principal.multiply(rate, MathContext.DECIMAL128)
    return BigDecimal(termMonths + 1).multiply(x, MathContext.DECIMAL128)
}

/**
 * Compute APR (annual percentage rate), sometimes referred to effective
 * interest rate
 */
fun effectiveInterestRate(
    loanAmount: Int,
    nominalAnnualInterestRate: Double,
    termFee: Int,
    termMonths: Int,
    loanType: AmortizationType
): BigDecimal =
    if(loanType == AmortizationType.STRAIGHT_LINE)
        throw UnsupportedOperationException()
    else {
        val totalCost: BigDecimal = totalCostOfLoan(loanAmount, nominalAnnualInterestRate, termFee, termMonths)
        val monthlyPayment: Double = totalCost.divide(BigDecimal(termMonths), MathContext.DECIMAL128).toDouble()
        effectiveInterestRateAnnuity(loanAmount, monthlyPayment, termMonths)
    }

private tailrec fun effectiveInterestRateAnnuity(
    loanAmount: Int,
    monthlyPayment: Double,
    paymentTerms: Int,
    accuracy: Double = 0.00000001,
    y: Double = 1.0
): BigDecimal {
    if(y <= accuracy)
        return BigDecimal(y).pow(-12, MathContext.DECIMAL128).minus(BigDecimal.ONE)

    val deltaY: Double = - f(y, loanAmount, monthlyPayment, paymentTerms) / fPrime(y+accuracy, paymentTerms)
    return effectiveInterestRateAnnuity(loanAmount, monthlyPayment, paymentTerms, accuracy, y + deltaY)
}

private tailrec fun f(
    y: Double,
    loanAmount: Int,
    monthlyPayment: Double,
    totalPayments: Int,
    payment: Int = 1
): Double {
    if(payment == totalPayments || y == Double.POSITIVE_INFINITY)
        return y - (loanAmount/monthlyPayment)
    val yn = Math.pow(y, payment.toDouble())
    return f(y+yn, loanAmount, monthlyPayment, totalPayments, payment + 1)
}

private tailrec fun fPrime(
    y: Double,
    totalPayments: Int,
    payment: Int = 1,
    sum: Double = 0.0
): Double {
    if(payment == totalPayments || y == Double.POSITIVE_INFINITY)
        return 1 + sum
    val yPol: Double = Math.pow((payment+1.0) * y, payment.toDouble())
    return fPrime(y, totalPayments, payment + 1, sum + yPol)
}
