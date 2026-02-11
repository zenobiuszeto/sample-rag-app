package com.bankrag.generator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * High-performance synthetic banking data generator using raw JDBC batch inserts.
 * Generates 100k customers, ~250k accounts, and ~5M+ transactions.
 */
@Service
public class SyntheticDataGenerator {

    private static final Logger log = LoggerFactory.getLogger(SyntheticDataGenerator.class);

    private final JdbcTemplate jdbcTemplate;

    @Value("${synthetic.customers:100000}")
    private int numCustomers;

    @Value("${synthetic.min-accounts-per-customer:1}")
    private int minAccounts;

    @Value("${synthetic.max-accounts-per-customer:4}")
    private int maxAccounts;

    @Value("${synthetic.min-transactions-per-account:5}")
    private int minTransactions;

    @Value("${synthetic.max-transactions-per-account:50}")
    private int maxTransactions;

    private static final String[] FIRST_NAMES = {
        "James","Mary","Robert","Patricia","John","Jennifer","Michael","Linda",
        "David","Elizabeth","William","Barbara","Richard","Susan","Joseph","Jessica",
        "Thomas","Sarah","Christopher","Karen","Charles","Lisa","Daniel","Nancy",
        "Matthew","Betty","Anthony","Margaret","Mark","Sandra","Donald","Ashley",
        "Steven","Dorothy","Paul","Kimberly","Andrew","Emily","Joshua","Donna",
        "Kenneth","Michelle","Kevin","Carol","Brian","Amanda","George","Melissa",
        "Timothy","Deborah","Ronald","Stephanie","Edward","Rebecca","Jason","Sharon",
        "Jeffrey","Laura","Ryan","Cynthia","Jacob","Kathleen","Gary","Amy",
        "Nicholas","Angela","Eric","Shirley","Jonathan","Anna","Stephen","Brenda",
        "Larry","Pamela","Justin","Emma","Scott","Nicole","Brandon","Helen",
        "Benjamin","Samantha","Samuel","Katherine","Raymond","Christine","Gregory","Debra",
        "Frank","Rachel","Alexander","Carolyn","Patrick","Janet","Jack","Catherine",
        "Wei","Priya","Mohammed","Fatima","Carlos","Maria","Hiroshi","Yuki",
        "Raj","Lakshmi","Ahmed","Aisha","Luis","Ana","Chen","Mei"
    };

    private static final String[] LAST_NAMES = {
        "Smith","Johnson","Williams","Brown","Jones","Garcia","Miller","Davis",
        "Rodriguez","Martinez","Hernandez","Lopez","Gonzalez","Wilson","Anderson",
        "Thomas","Taylor","Moore","Jackson","Martin","Lee","Perez","Thompson",
        "White","Harris","Sanchez","Clark","Ramirez","Lewis","Robinson","Walker",
        "Young","Allen","King","Wright","Scott","Torres","Nguyen","Hill",
        "Flores","Green","Adams","Nelson","Baker","Hall","Rivera","Campbell",
        "Mitchell","Carter","Roberts","Gomez","Phillips","Evans","Turner","Diaz",
        "Parker","Cruz","Edwards","Collins","Reyes","Stewart","Morris","Morales",
        "Murphy","Cook","Rogers","Gutierrez","Ortiz","Morgan","Cooper","Peterson",
        "Bailey","Reed","Kelly","Howard","Ramos","Kim","Cox","Ward",
        "Richardson","Watson","Brooks","Chavez","Wood","James","Bennett","Gray",
        "Mendoza","Ruiz","Hughes","Price","Alvarez","Castillo","Sanders","Patel",
        "Wang","Chen","Singh","Kumar","Tanaka","Yamamoto","Park","Choi"
    };

    private static final String[] CITIES = {
        "New York","Los Angeles","Chicago","Houston","Phoenix","Philadelphia",
        "San Antonio","San Diego","Dallas","San Jose","Austin","Jacksonville",
        "Fort Worth","Columbus","Charlotte","Indianapolis","San Francisco","Seattle",
        "Denver","Nashville","Oklahoma City","Portland","Las Vegas","Memphis",
        "Louisville","Baltimore","Milwaukee","Albuquerque","Tucson","Fresno",
        "Sacramento","Mesa","Atlanta","Omaha","Raleigh","Miami","Cleveland",
        "Tampa","Minneapolis","Pittsburgh","St. Louis","Cincinnati","Orlando"
    };

    private static final String[] STATES = {
        "NY","CA","IL","TX","AZ","PA","TX","CA","TX","CA","TX","FL",
        "TX","OH","NC","IN","CA","WA","CO","TN","OK","OR","NV","TN",
        "KY","MD","WI","NM","AZ","CA","CA","AZ","GA","NE","NC","FL",
        "OH","FL","MN","PA","MO","OH","FL"
    };

    private static final String[] STREETS = {
        "Main St","Oak Ave","Maple Dr","Cedar Ln","Elm St","Park Ave",
        "Washington Blvd","Lake Dr","Hill Rd","Forest Way","River Rd",
        "Spring St","Valley View Dr","Sunset Blvd","Highland Ave",
        "Broadway","Market St","Pine St","Walnut St","Cherry Ln"
    };

    private static final String[] ACCOUNT_TYPES = {"CHECKING","SAVINGS","CREDIT_CARD","MORTGAGE","LOAN"};
    private static final String[] CHANNELS = {"ONLINE","BRANCH","ATM","MOBILE","POS"};
    private static final String[] TX_STATUSES = {"COMPLETED","COMPLETED","COMPLETED","PENDING","FAILED"};

    private static final String[][] MERCHANT_DATA = {
        {"GROCERIES","Whole Foods","Trader Joe's","Kroger","Safeway","Costco","Walmart Grocery","Aldi","Publix"},
        {"RESTAURANTS","Chipotle","Olive Garden","Starbucks","McDonald's","Panera Bread","Chick-fil-A","Subway","Domino's"},
        {"GAS_STATION","Shell","Chevron","BP","Exxon","Mobil","Sunoco","Valero","Citgo"},
        {"SHOPPING","Amazon","Target","Walmart","Best Buy","Macy's","Nordstrom","Home Depot","Ikea"},
        {"UTILITIES","Electric Company","Water Services","Gas Utility","Internet Provider","Phone Service"},
        {"HEALTHCARE","CVS Pharmacy","Walgreens","Kaiser Permanente","UnitedHealth","Blue Cross"},
        {"ENTERTAINMENT","Netflix","Spotify","AMC Theaters","Disney+","HBO Max","Apple Music","Steam"},
        {"TRAVEL","United Airlines","Delta Airlines","Marriott","Hilton","Hertz","Uber","Lyft","Airbnb"},
        {"INSURANCE","State Farm","Geico","Progressive","Allstate","Liberty Mutual"},
        {"EDUCATION","University Tuition","Student Bookstore","Coursera","Udemy","Pearson"}
    };

    public SyntheticDataGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public GenerationStats generate() {
        Long existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customers", Long.class);
        if (existing != null && existing > 0) {
            log.info("Data already exists ({} customers), skipping generation.", existing);
            return new GenerationStats(0, 0, 0, true);
        }

        log.info("Starting FAST synthetic data generation: {} customers...", numCustomers);
        long startTime = System.currentTimeMillis();

        Random rng = new Random(42);
        int totalAccounts = 0;
        int totalTransactions = 0;

        // Disable indexes for faster bulk insert
        try { jdbcTemplate.execute("SET session_replication_role = 'replica'"); } catch (Exception e) { /* ignore */ }

        int custBatch = 2000;
        for (int offset = 0; offset < numCustomers; offset += custBatch) {
            int end = Math.min(offset + custBatch, numCustomers);
            int batchLen = end - offset;

            // --- Insert customers ---
            String custSql = "INSERT INTO customers (customer_id, first_name, last_name, email, phone, " +
                "date_of_birth, ssn_last4, address_line1, address_city, address_state, address_zip, " +
                "credit_score, customer_since, segment, risk_rating, created_at, updated_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW())";

            // We need to track generated data to create accounts
            String[][] custData = new String[batchLen][];
            int[] creditScores = new int[batchLen];
            LocalDate[] custSinceDates = new LocalDate[batchLen];

            // Make variables effectively final for lambda usage
            final int batchOffset = offset;
            final Random random = rng;

            jdbcTemplate.batchUpdate(custSql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                    int idx = batchOffset + i;
                    String fn = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
                    String ln = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
                    int cityIdx = random.nextInt(CITIES.length);
                    int cs = 300 + random.nextInt(551);
                    LocalDate since = LocalDate.of(2005 + random.nextInt(20), 1 + random.nextInt(12), 1 + random.nextInt(28));
                    int segRoll = random.nextInt(100);
                    String seg = segRoll < 70 ? "RETAIL" : segRoll < 90 ? "PREMIUM" : "PRIVATE_BANKING";
                    String risk = cs >= 700 ? "LOW" : cs >= 580 ? "MEDIUM" : "HIGH";

                    String custId = String.format("CUST-%06d", idx + 1);
                    ps.setString(1, custId);
                    ps.setString(2, fn);
                    ps.setString(3, ln);
                    ps.setString(4, fn.toLowerCase() + "." + ln.toLowerCase() + idx + "@email.com");
                    ps.setString(5, String.format("(%03d) %03d-%04d", random.nextInt(900)+100, random.nextInt(900)+100, random.nextInt(10000)));
                    ps.setDate(6, Date.valueOf(LocalDate.of(1950+random.nextInt(55), 1+random.nextInt(12), 1+random.nextInt(28))));
                    ps.setString(7, String.format("%04d", random.nextInt(10000)));
                    ps.setString(8, (100+random.nextInt(9900)) + " " + STREETS[random.nextInt(STREETS.length)]);
                    ps.setString(9, CITIES[cityIdx]);
                    ps.setString(10, STATES[cityIdx]);
                    ps.setString(11, String.format("%05d", 10000+random.nextInt(90000)));
                    ps.setInt(12, cs);
                    ps.setDate(13, Date.valueOf(since));
                    ps.setString(14, seg);
                    ps.setString(15, risk);

                    custData[i] = new String[]{custId, fn, ln, seg, risk};
                    creditScores[i] = cs;
                    custSinceDates[i] = since;
                }
                @Override
                public int getBatchSize() { return batchLen; }
            });

            // Get generated customer IDs (DB primary keys)
            List<long[]> custIds = jdbcTemplate.query(
                "SELECT id, customer_id FROM customers WHERE customer_id >= ? AND customer_id <= ? ORDER BY id",
                (rs, rowNum) -> new long[]{rs.getLong("id")},
                String.format("CUST-%06d", offset + 1),
                String.format("CUST-%06d", end)
            );

            // --- Insert accounts ---
            String acctSql = "INSERT INTO accounts (account_number, customer_id, account_type, account_name, " +
                "balance, currency, interest_rate, credit_limit, status, opened_date, closed_date, created_at, updated_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW())";

            List<Object[]> acctRows = new ArrayList<>();
            // Track for transaction generation
            List<long[]> acctMeta = new ArrayList<>(); // {custDbId, acctType_idx, balance*100, openedDate_epoch}

            for (int i = 0; i < custIds.size(); i++) {
                long custDbId = custIds.get(i)[0];
                String fn = custData[i][1];
                LocalDate since = custSinceDates[i];
                int numAccts = minAccounts + rng.nextInt(maxAccounts - minAccounts + 1);

                for (int a = 0; a < numAccts; a++) {
                    String acctNum = String.format("%04d-%04d-%04d", rng.nextInt(10000), rng.nextInt(10000), rng.nextInt(10000));
                    String type = (a == 0) ? "CHECKING" : ACCOUNT_TYPES[rng.nextInt(ACCOUNT_TYPES.length)];
                    String name = fn + "'s " + type.replace("_", " ");

                    BigDecimal balance, rate = null, limit = null;
                    switch (type) {
                        case "SAVINGS":
                            balance = rbd(500, 200000, rng);
                            rate = rbd(0.02, 0.05, rng).setScale(4, RoundingMode.HALF_UP);
                            break;
                        case "CREDIT_CARD":
                            limit = rbd(1000, 50000, rng);
                            balance = rbd(0, limit.doubleValue() * 0.8, rng).negate();
                            rate = rbd(0.14, 0.26, rng).setScale(4, RoundingMode.HALF_UP);
                            break;
                        case "MORTGAGE":
                            balance = rbd(50000, 800000, rng).negate();
                            rate = rbd(0.03, 0.07, rng).setScale(4, RoundingMode.HALF_UP);
                            break;
                        case "LOAN":
                            balance = rbd(1000, 50000, rng).negate();
                            rate = rbd(0.04, 0.15, rng).setScale(4, RoundingMode.HALF_UP);
                            break;
                        default: // CHECKING
                            balance = rbd(100, 50000, rng);
                            rate = new BigDecimal("0.0010");
                            break;
                    }

                    int statusRoll = rng.nextInt(100);
                    String status = statusRoll < 90 ? "ACTIVE" : statusRoll < 95 ? "FROZEN" : "CLOSED";
                    LocalDate opened = since.plusDays(rng.nextInt(365));
                    LocalDate closed = "CLOSED".equals(status) ? opened.plusYears(1 + rng.nextInt(5)) : null;

                    acctRows.add(new Object[]{
                        acctNum, custDbId, type, name, balance, "USD", rate, limit,
                        status, Date.valueOf(opened), closed != null ? Date.valueOf(closed) : null
                    });

                    int typeIdx = Arrays.asList(ACCOUNT_TYPES).indexOf(type);
                    acctMeta.add(new long[]{custDbId, typeIdx, balance.abs().multiply(BigDecimal.valueOf(100)).longValue(), opened.toEpochDay()});
                    totalAccounts++;
                }
            }

            jdbcTemplate.batchUpdate(acctSql, acctRows, acctRows.size(), (ps, row) -> {
                ps.setString(1, (String) row[0]);
                ps.setLong(2, (long) row[1]);
                ps.setString(3, (String) row[2]);
                ps.setString(4, (String) row[3]);
                ps.setBigDecimal(5, (BigDecimal) row[4]);
                ps.setString(6, (String) row[5]);
                if (row[6] != null) ps.setBigDecimal(7, (BigDecimal) row[6]); else ps.setNull(7, java.sql.Types.DECIMAL);
                if (row[7] != null) ps.setBigDecimal(8, (BigDecimal) row[7]); else ps.setNull(8, java.sql.Types.DECIMAL);
                ps.setString(9, (String) row[8]);
                ps.setDate(10, (Date) row[9]);
                if (row[10] != null) ps.setDate(11, (Date) row[10]); else ps.setNull(11, java.sql.Types.DATE);
            });

            // Get account DB IDs
            long minCustId = custIds.get(0)[0];
            long maxCustId = custIds.get(custIds.size() - 1)[0];
            List<long[]> acctIds = jdbcTemplate.query(
                "SELECT id, customer_id FROM accounts WHERE customer_id BETWEEN ? AND ? ORDER BY id",
                (rs, rowNum) -> new long[]{rs.getLong("id"), rs.getLong("customer_id")},
                minCustId, maxCustId
            );

            // --- Insert transactions ---
            String txSql = "INSERT INTO transactions (transaction_id, account_id, transaction_type, amount, " +
                "balance_after, description, merchant_name, merchant_category, channel, status, " +
                "reference_number, transaction_date, posted_date, created_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

            List<Object[]> txRows = new ArrayList<>(acctIds.size() * 20);

            for (int ai = 0; ai < acctIds.size(); ai++) {
                long acctDbId = acctIds.get(ai)[0];
                int typeIdx = (ai < acctMeta.size()) ? (int) acctMeta.get(ai)[1] : 0;
                String acctType = ACCOUNT_TYPES[Math.max(0, Math.min(typeIdx, ACCOUNT_TYPES.length - 1))];
                long balanceCents = (ai < acctMeta.size()) ? acctMeta.get(ai)[2] : 500000;
                long openedEpoch = (ai < acctMeta.size()) ? acctMeta.get(ai)[3] : LocalDate.of(2020,1,1).toEpochDay();

                BigDecimal running = BigDecimal.valueOf(balanceCents, 2);
                LocalDateTime base = LocalDate.ofEpochDay(openedEpoch).atStartOfDay();
                LocalDateTime now = LocalDateTime.now();
                long totalDays = java.time.temporal.ChronoUnit.DAYS.between(base, now);
                if (totalDays <= 0) totalDays = 365;

                int numTx = minTransactions + rng.nextInt(maxTransactions - minTransactions + 1);
                for (int t = 0; t < numTx; t++) {
                    long dayOff = (long)(rng.nextDouble() * totalDays);
                    LocalDateTime txDate = base.plusDays(dayOff).plusHours(rng.nextInt(24)).plusMinutes(rng.nextInt(60));
                    if (txDate.isAfter(now)) txDate = now.minusDays(rng.nextInt(30)+1);

                    int catIdx = rng.nextInt(MERCHANT_DATA.length);
                    String category = MERCHANT_DATA[catIdx][0];
                    String merchant = MERCHANT_DATA[catIdx][1 + rng.nextInt(MERCHANT_DATA[catIdx].length - 1)];

                    String txType; BigDecimal amt; String desc; String mn = null; String mc = null;

                    switch (acctType) {
                        case "CHECKING": case "SAVINGS":
                            int roll = rng.nextInt(100);
                            if (roll < 30) {
                                txType = "DEPOSIT"; amt = rbd(50,5000,rng);
                                desc = "Direct deposit" + (rng.nextBoolean() ? " - payroll" : "");
                                running = running.add(amt);
                            } else if (roll < 35) {
                                txType = "FEE"; amt = rbd(5,35,rng);
                                desc = "Monthly maintenance fee";
                                running = running.subtract(amt);
                            } else if (roll < 45) {
                                txType = "TRANSFER"; amt = rbd(100,2000,rng);
                                desc = "Transfer to savings";
                                running = running.subtract(amt);
                            } else {
                                txType = "WITHDRAWAL"; amt = rbd(5,500,rng);
                                desc = "Purchase at " + merchant; mn = merchant; mc = category;
                                running = running.subtract(amt);
                            }
                            break;
                        case "CREDIT_CARD":
                            if (rng.nextInt(100) < 20) {
                                txType = "PAYMENT"; amt = rbd(50,2000,rng);
                                desc = "Credit card payment"; running = running.subtract(amt);
                            } else {
                                txType = "PAYMENT"; amt = rbd(10,1000,rng);
                                desc = "Purchase: " + merchant; mn = merchant; mc = category;
                                running = running.add(amt);
                            }
                            break;
                        default: // MORTGAGE, LOAN
                            txType = "PAYMENT"; amt = rbd(200,3000,rng);
                            desc = "Monthly " + acctType.toLowerCase() + " payment";
                            running = running.subtract(amt);
                            break;
                    }

                    Timestamp ts = Timestamp.valueOf(txDate);
                    Timestamp posted = Timestamp.valueOf(txDate.plusHours(rng.nextInt(48)));

                    txRows.add(new Object[]{
                        UUID.randomUUID().toString(), acctDbId, txType, amt, running.abs(),
                        desc, mn, mc, CHANNELS[rng.nextInt(CHANNELS.length)],
                        TX_STATUSES[rng.nextInt(TX_STATUSES.length)],
                        "REF-" + UUID.randomUUID().toString().substring(0,8).toUpperCase(),
                        ts, posted, ts
                    });
                    totalTransactions++;
                }

                // Flush transaction batch every 50k rows
                if (txRows.size() >= 50000) {
                    flushTransactions(txSql, txRows);
                    txRows.clear();
                }
            }

            if (!txRows.isEmpty()) {
                flushTransactions(txSql, txRows);
                txRows.clear();
            }

            if ((end) % 10000 == 0 || end == numCustomers) {
                log.info("Generated {}/{} customers, {} accounts, {} transactions so far...",
                    end, numCustomers, totalAccounts, totalTransactions);
            }
        }

        // Re-enable constraints
        try { jdbcTemplate.execute("SET session_replication_role = 'origin'"); } catch (Exception e) { /* ignore */ }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Data generation complete in {}s: {} customers, {} accounts, {} transactions",
                elapsed / 1000, numCustomers, totalAccounts, totalTransactions);

        return new GenerationStats(numCustomers, totalAccounts, totalTransactions, false);
    }

    private void flushTransactions(String sql, List<Object[]> rows) {
        jdbcTemplate.batchUpdate(sql, rows, rows.size(), (ps, row) -> {
            ps.setString(1, (String) row[0]);
            ps.setLong(2, (long) row[1]);
            ps.setString(3, (String) row[2]);
            ps.setBigDecimal(4, (BigDecimal) row[3]);
            ps.setBigDecimal(5, (BigDecimal) row[4]);
            ps.setString(6, (String) row[5]);
            if (row[6] != null) ps.setString(7, (String) row[6]); else ps.setNull(7, java.sql.Types.VARCHAR);
            if (row[7] != null) ps.setString(8, (String) row[7]); else ps.setNull(8, java.sql.Types.VARCHAR);
            ps.setString(9, (String) row[8]);
            ps.setString(10, (String) row[9]);
            ps.setString(11, (String) row[10]);
            ps.setTimestamp(12, (Timestamp) row[11]);
            ps.setTimestamp(13, (Timestamp) row[12]);
            ps.setTimestamp(14, (Timestamp) row[13]);
        });
    }

    private BigDecimal rbd(double min, double max, Random r) {
        return BigDecimal.valueOf(min + (max - min) * r.nextDouble()).setScale(2, RoundingMode.HALF_UP);
    }

    public record GenerationStats(int customers, int accounts, int transactions, boolean skipped) {}
}
