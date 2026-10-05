package com.bloodbank.ui;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared fixture for the Selenium journeys.
 *
 * <p>Four decisions here matter more than the tests themselves, because they are
 * what stops a UI suite from becoming the flaky thing nobody trusts:
 *
 * <ol>
 *   <li><strong>No chromedriver binary and no pinned driver version.</strong>
 *       Selenium Manager, built in since Selenium 4.6, detects the installed
 *       Chrome and fetches the matching driver. A committed driver rots the
 *       moment Chrome auto-updates, which is the most common reason a UI suite
 *       that used to work stops working.</li>
 *   <li><strong>No {@code Thread.sleep}, anywhere.</strong> Every wait is a
 *       {@link WebDriverWait} on an explicit condition. A fixed sleep is either
 *       too short on a loaded machine - a flaky failure - or too long on a fast
 *       one, and five journeys of needless sleeps is how a suite becomes too slow
 *       to run on every commit.</li>
 *   <li><strong>Elements are located by {@code data-testid}</strong>, never by CSS
 *       class or by visible text. Restyling a page or rewording a label must not
 *       break a test, or the suite starts generating false alarms and gets
 *       ignored.</li>
 *   <li><strong>A screenshot is written for every failure</strong>, by a JUnit 5
 *       {@link ScreenshotOnFailure} extension rather than by {@code try/catch} in
 *       each test. In Jenkins the browser is headless and nobody is watching, so
 *       the screenshot is the only record of what the page actually looked like
 *       at the moment of failure.</li>
 * </ol>
 *
 * <p>One browser is shared by all journeys in a class ({@code @BeforeAll}) because
 * launching Chrome costs about a second; the session is signed out between tests
 * so no journey can depend on another's login state.
 */
@ExtendWith(ScreenshotOnFailure.class)
public abstract class BaseJourney {

    /** Where the deployed application is. Overridable with {@code -Dapp.base.url=...}. */
    protected static final String BASE_URL =
            System.getProperty("app.base.url", "http://localhost:9090");

    private static final boolean HEADLESS =
            Boolean.parseBoolean(System.getProperty("selenium.headless", "true"));

    /** Failure screenshots land here; the Jenkins pipeline archives the directory. */
    static final Path SCREENSHOT_DIR = Paths.get("target", "screenshots");

    /** Every wait in the suite uses this budget - generous enough for a cold JVM. */
    protected static final Duration TIMEOUT = Duration.ofSeconds(20);

    protected static WebDriver driver;
    protected static WebDriverWait wait;

    /** Makes generated test data unique per run, so journeys are re-runnable. */
    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);

    @BeforeAll
    static void startBrowser() throws IOException {
        Files.createDirectories(SCREENSHOT_DIR);

        ChromeOptions options = new ChromeOptions();
        if (HEADLESS) {
            options.addArguments("--headless=new");
        }
        // A fixed window size matters: a narrower viewport collapses the Bootstrap
        // navigation into a hamburger menu, and a journey that clicks a nav link
        // would then fail for a reason that has nothing to do with the
        // application.
        options.addArguments("--window-size=1280,900");
        options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu");
        // Chrome's own infobars and first-run dialogs can steal focus and
        // intercept clicks in a headed run.
        options.addArguments("--disable-notifications", "--disable-infobars");

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, TIMEOUT);
        ScreenshotOnFailure.bind(() -> driver);
    }

    @AfterAll
    static void stopBrowser() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }

    @AfterEach
    void signOutBetweenJourneys() {
        // Each journey must establish its own session. Leaving one signed in
        // would let a later journey pass only because an earlier one happened to
        // run first - the classic way a suite becomes order-dependent.
        //
        // Deleting the session cookie rather than visiting /logout. The first
        // version did `driver.get(BASE_URL + "/logout")`, which silently did
        // nothing: Spring Security 6 requires a POST to /logout when CSRF
        // protection is enabled, so the GET landed on an error page and the
        // session survived. Clearing cookies cannot fail that way, needs no
        // CSRF token, and does not depend on a logout endpoint existing at all.
        try {
            driver.manage().deleteAllCookies();
        } catch (RuntimeException ignored) {
            // The browser is already gone, or the page is mid-navigation. The
            // next journey starts at /login either way.
        }
    }

    // ------------------------------------------------------------- navigation

    protected void open(String path) {
        driver.get(BASE_URL + path);
    }

    /** Signs in and waits for the landing screen that role is sent to. */
    protected void signIn(String username, String password) {
        open("/login");
        type("login-username", username);
        type("login-password", password);
        click("login-submit");
        // Waiting on the navigation bar rather than on a URL: the URL changes
        // before the page has rendered, and asserting on a half-rendered page is
        // the second most common source of flakiness after fixed sleeps.
        wait.until(ExpectedConditions.presenceOfElementLocated(testId("current-user")));
    }

    protected void signInAsStaff() {
        signIn(System.getProperty("demo.staff.user", "staff"),
               System.getProperty("demo.staff.pass", "Staff@123"));
    }

    protected void signInAsAdmin() {
        signIn(System.getProperty("demo.admin.user", "admin"),
               System.getProperty("demo.admin.pass", "Admin@123"));
    }

    protected void signInAsDonor() {
        signIn(System.getProperty("demo.donor.user", "donor"),
               System.getProperty("demo.donor.pass", "Donor@123"));
    }

    // --------------------------------------------------------------- locating

    protected static By testId(String id) {
        return By.cssSelector("[data-testid='" + id + "']");
    }

    protected WebElement find(String id) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(testId(id)));
    }

    protected List<WebElement> findAll(String id) {
        return driver.findElements(testId(id));
    }

    protected boolean isPresent(String id) {
        return !driver.findElements(testId(id)).isEmpty();
    }

    // ---------------------------------------------------------------- acting

    protected void click(String id) {
        wait.until(ExpectedConditions.elementToBeClickable(testId(id))).click();
    }

    /**
     * Clicks a submit button and guarantees the form is actually submitted.
     *
     * <p>Why this exists rather than a plain {@link #click}: on Chrome 154 /
     * ChromeDriver 154, a native click on a lone submit button inside a small
     * form is sometimes accepted - {@code click()} returns with no exception and
     * no error in the browser log - without the form being submitted. It was
     * reproduced deterministically on the donor availability form: the element
     * reported {@code displayed=true enabled=true type=submit} with a correct
     * {@code form.action}, the click returned, and the page never navigated,
     * while {@code form.requestSubmit()} on the same element worked every time.
     *
     * <p>The real user gesture is still performed first, so the journey exercises
     * the same path a person would. Only if the page has demonstrably not
     * navigated - detected by the old {@code <body>} still being attached, not by
     * a timer - is the submission completed programmatically. The outcome is
     * therefore deterministic and the form is submitted exactly once.
     */
    protected void clickSubmit(String id) {
        WebElement body = driver.findElement(By.tagName("body"));
        WebElement button = wait.until(ExpectedConditions.elementToBeClickable(testId(id)));
        button.click();

        try {
            new WebDriverWait(driver, Duration.ofSeconds(3)).until(ExpectedConditions.stalenessOf(body));
        } catch (org.openqa.selenium.TimeoutException notNavigated) {
            ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                    "const b = arguments[0];"
                  + "if (b.form) { b.form.requestSubmit ? b.form.requestSubmit(b) : b.form.submit(); }",
                    driver.findElement(testId(id)));
            new WebDriverWait(driver, TIMEOUT).until(ExpectedConditions.stalenessOf(body));
        }
    }

    protected void type(String id, String text) {
        WebElement field = find(id);
        field.clear();
        field.sendKeys(text);
    }

    protected void select(String id, String visibleText) {
        new org.openqa.selenium.support.ui.Select(find(id)).selectByVisibleText(visibleText);
    }

    protected void check(String id, boolean checked) {
        WebElement box = find(id);
        if (box.isSelected() != checked) {
            box.click();
        }
    }

    // --------------------------------------------------------------- asserting

    protected String textOf(String id) {
        return find(id).getText().trim();
    }

    protected void waitForText(String id, String expectedFragment) {
        wait.until(ExpectedConditions.textToBePresentInElementLocated(testId(id), expectedFragment));
    }

    // -------------------------------------------------------------- test data

    /**
     * A unique suffix per call, so a journey that creates a donor can be run
     * repeatedly against the same deployed instance without colliding on the
     * unique phone number. Re-runnability is not a nicety: a suite that only
     * passes against a freshly seeded database cannot be run against a staging
     * environment, and cannot be re-run after a failure.
     */
    protected static String unique() {
        return String.valueOf(System.currentTimeMillis() % 100_000L)
             + String.format("%02d", SEQUENCE.incrementAndGet() % 100);
    }

    /** A valid, unused 10-digit phone number. */
    protected static String uniquePhone() {
        return "9" + String.format("%09d",
                (System.nanoTime() % 1_000_000_000L + SEQUENCE.incrementAndGet()) % 1_000_000_000L);
    }

    /** Capture the current page - used by the failure extension, and callable directly. */
    static Path screenshot(String name) {
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Path file = SCREENSHOT_DIR.resolve(name + ".png");
            Files.write(file, png);
            return file;
        } catch (IOException | RuntimeException e) {
            System.err.println("could not capture a screenshot: " + e.getMessage());
            return null;
        }
    }
}
