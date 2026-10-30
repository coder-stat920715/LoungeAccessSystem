package com.lounge.access.service.statement;

import com.lounge.access.dto.StatementCategory;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Keyword-based classifier. Order matters: the three programme exclusions (cash, wallet, fuel) and fees are tested
 * before ordinary merchant categories so that, for example, "PAYTM ADD MONEY" is never counted as shopping.
 */
@Component
public class TransactionCategorizer {

    private final Map<StatementCategory, Pattern> rules = new LinkedHashMap<>();

    public TransactionCategorizer() {
        add(StatementCategory.CASH_WITHDRAWAL, "atm", "cash withdrawal", "cash wdl", "cash advance", "cash adv",
                "nfs cash");
        add(StatementCategory.WALLET_RELOAD, "wallet", "add money", "mobikwik", "freecharge", "amazon pay balance",
                "gift card", "prepaid card load", "money added");
        add(StatementCategory.FUEL, "fuel", "petrol", "diesel", "hpcl", "bpcl", "iocl", "indian oil", "indianoil",
                "bharat petroleum", "hindustan petroleum", "reliance petro", "jio-bp", "jio bp", "nayara",
                "essar oil", "cng", "petroleum", "filling station", "shell india", "shell petrol");
        add(StatementCategory.FEES_AND_CHARGES, "annual fee", "joining fee", "renewal fee", "late fee",
                "late payment", "finance charge", "interest", "igst", "cgst", "sgst", "gst", "service charge",
                "fees & charges", "fees and charges", "overlimit");
        add(StatementCategory.TRAVEL, "indigo", "air india", "vistara", "spicejet", "akasa", "airline", "airways",
                "makemytrip", "goibibo", "cleartrip", "irctc", "railway", "uber", "ola cabs", "rapido", "yatra",
                "hotel", "marriott", "oyo", "airbnb", "booking.com", "emirates", "flight", "airport", "redbus",
                "travel", "taj ");
        add(StatementCategory.DINING, "restaurant", "cafe", "coffee", "starbucks", "zomato", "swiggy", "dominos",
                "mcdonald", "kfc", "pizza", "barbeque", "dineout", "eatery", "bakery", "dining", "food");
        add(StatementCategory.GROCERY, "bigbasket", "grofers", "blinkit", "zepto", "dmart", "d-mart",
                "reliance fresh", "nature's basket", "supermarket", "grocery", "instamart", "jiomart", "spencer");
        add(StatementCategory.ENTERTAINMENT, "netflix", "hotstar", "spotify", "bookmyshow", "pvr", "inox",
                "prime video", "youtube", "cinema", "movie", "gaming", "entertainment");
        add(StatementCategory.UTILITIES, "electricity", "bescom", "tata power", "adani electricity", "mseb",
                "water bill", "gas bill", "piped gas", "mahanagar gas", "indraprastha gas", "airtel", "vodafone",
                "bsnl", "broadband", "dth", "recharge", "tata play", "fibernet", "postpaid", "bbps", "telecom",
                "utilit");
        add(StatementCategory.HEALTH, "pharmacy", "apollo", "medplus", "hospital", "clinic", "diagnostic", "1mg",
                "pharmeasy", "lenskart", "dental", "netmeds", "medical");
        add(StatementCategory.EDUCATION, "school", "college", "university", "tuition", "udemy", "coursera",
                "byju", "unacademy", "education");
        add(StatementCategory.INSURANCE, "insurance", "hdfc life", "icici pru", "policybazaar", "star health",
                "max life", "bajaj allianz", "acko");
        add(StatementCategory.SHOPPING, "amazon", "flipkart", "myntra", "ajio", "nykaa", "croma",
                "reliance digital", "apple", "lifestyle", "westside", "shoppers stop", "tanishq", "decathlon",
                "ikea", "pantaloons", "zara", "uniqlo", "meesho", "tata cliq", "retail", "mall", "store",
                "e-commerce", "ecommerce", "shopping", "mart");
    }

    private void add(StatementCategory category, String... keywords) {
        StringBuilder regex = new StringBuilder("(?<![a-z0-9])(?:");
        for (int i = 0; i < keywords.length; i++) {
            if (i > 0) {
                regex.append('|');
            }
            regex.append(Pattern.quote(keywords[i]));
        }
        regex.append(')');
        rules.put(category, Pattern.compile(regex.toString()));
    }

    /** @param rawCategory merchant-category text from the statement, may be null */
    public StatementCategory categorize(String description, String rawCategory) {
        String text = ((description == null ? "" : description) + " " + (rawCategory == null ? "" : rawCategory))
                .toLowerCase(Locale.ROOT);
        for (Map.Entry<StatementCategory, Pattern> rule : rules.entrySet()) {
            if (rule.getValue().matcher(text).find()) {
                return rule.getKey();
            }
        }
        return StatementCategory.OTHER;
    }
}
