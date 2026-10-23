import app.teakadai.reader.Allowlist
import app.teakadai.reader.ParsedPayment
import app.teakadai.reader.PaymentParser

var checks = 0
var failures = 0

fun <T> check(name: String, actual: T, expected: T) {
    checks++
    if (actual != expected) { failures++; println("FAIL  $name\n   expected: $expected\n   actual:   $actual") }
}

fun main() {
    fun p(a: Long, n: String?) = ParsedPayment(a, n)

    // Accepted: real samples from the owner's phone and the usual wording of each app.
    check("gpay real", PaymentParser.parse("N MUTHAIAH paid you ₹1.00", "Sent using Paytm UPI"), p(100, "N MUTHAIAH"))
    check("gpay one line", PaymentParser.parse("", "N MUTHAIAH paid you ₹1.00: Sent using Paytm UPI"), p(100, "N MUTHAIAH"))
    check("gpay big", PaymentParser.parse("Ravi Kumar paid you ₹1,250.50", ""), p(125050, "Ravi Kumar"))
    check("sent you", PaymentParser.parse("Meena sent you ₹30", ""), p(3000, "Meena"))
    check("phonepe received from", PaymentParser.parse("Payment received", "Received ₹45 from RAJ KUMAR"), p(4500, "RAJ KUMAR"))
    check("received from, trailing", PaymentParser.parse("Received ₹45 from Raj Kumar on 12 Oct", ""), p(4500, "Raj Kumar"))
    check("paytm amount first", PaymentParser.parse("₹15 received from Anitha", "Check your balance"), p(1500, "Anitha"))
    check("paytm business real", PaymentParser.parse("Received ₹1 from Nishanth Nellayappan", "Deposited in your Federal Bank - 8326 on 23 October at 2:22 PM"), p(100, "Nishanth Nellayappan"))
    check("paytm business body only", PaymentParser.parse("", "Received ₹1 from Nishanth Nellayappan Deposited in your Federal Bank - 8326 on 23 October at 2:22 PM"), p(100, "Nishanth Nellayappan"))
    check("paytm business package", Allowlist.label("com.paytm.business"), "Paytm for Business")
    check("phonepe real", PaymentParser.parse("Money received", "NISHANTH NELLAYAPPAN M has sent ₹1 to your bank account Federal Bank-8326"), p(100, "NISHANTH NELLAYAPPAN M"))
    check("phonepe bigger", PaymentParser.parse("Money received", "Raj Kumar has sent ₹1,250.50 to your bank account HDFC-1234"), p(125050, "Raj Kumar"))
    check("phonepe not to you", PaymentParser.parse("Money sent", "Raj has sent ₹50 to Meena"), null)
    check("phonepe you sent", PaymentParser.parse("Payment successful", "You sent ₹50 to your friend Meena"), null)
    check("credited no name", PaymentParser.parse("Rs.100.37 credited to A/c XX1234", ""), p(10037, null))
    check("credited with", PaymentParser.parse("A/c credited with Rs 45", ""), p(4500, null))
    check("amount received no name", PaymentParser.parse("₹20 received", ""), p(2000, null))
    check("cap exactly 10000", PaymentParser.parse("Raj paid you ₹10,000", ""), p(1000000, "Raj"))

    // Rejected: must never be treated as a customer payment.
    val rejected = listOf(
        "Mummy: Kitne paise bachaye?: You: Poore ₹400😌",
        "You paid ₹50 to Sri Tea Stall",
        "Rs.1.00 debited from A/c XX1234",
        "Payment successful ₹1",
        "Money sent to Raj ₹100",
        "Get ₹50 cashback on your next payment",
        "You won a reward of ₹100! Scratch now",
        "Raj requested ₹100 from you",
        "Reminder: pay ₹500 to Raj",
        "Your balance is ₹1,200",
        "Special offer: ₹499 recharge",
        "Refund of ₹100 initiated",
        "Payment of ₹100 failed",
        "Raj paid you ₹10,000.01",
        "Raj paid you ₹0",
        "Hello there",
        "",
    )
    for (t in rejected) check("reject: $t", PaymentParser.parse(t, ""), null)
    check("reject title+text chat", PaymentParser.parse("Mummy", "Kitne paise bachaye? Poore ₹400"), null)

    // Names
    check("name cleaned", PaymentParser.cleanName("  RAVI\u0000  KUMAR. "), "RAVI KUMAR")
    check("name too short", PaymentParser.cleanName("A"), null)
    check("name length", PaymentParser.cleanName("x".repeat(100))!!.length, 60)
    check("toPaise", PaymentParser.toPaise("1,25,000.5"), 12500050L)
    check("format", PaymentParser.format(105), "₹1.05")

    // Allowlist
    check("gpay allowed", Allowlist.isAllowed("com.google.android.apps.nbu.paisa.user"), true)
    check("whatsapp not allowed", Allowlist.isAllowed("com.whatsapp"), false)
    check("sms off", Allowlist.isAllowed("com.google.android.apps.messaging"), false)
    check("gmail off", Allowlist.isAllowed("com.google.android.gm"), false)
    check("lookalike", Allowlist.isAllowed("com.google.android.apps.nbu.paisa.user.evil"), false)

    println("$checks checks, $failures failures")
    if (failures > 0) System.exit(1)
}
