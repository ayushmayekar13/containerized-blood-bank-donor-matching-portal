package com.bloodbank.ui;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.openqa.selenium.WebDriver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Writes a screenshot and the page source whenever a journey fails.
 *
 * <p>A JUnit 5 {@link TestWatcher} rather than {@code try/catch} in each test: it
 * cannot be forgotten when a new journey is added, and it does not clutter the
 * journeys themselves with error handling that has nothing to do with what they
 * are asserting.
 *
 * <p>This exists because of where the suite actually runs. In Jenkins the browser
 * is headless and nobody is watching, so when a journey fails the only record of
 * what the page looked like is what gets written here. The pipeline archives
 * {@code target/screenshots/}, so the artefact is attached to the failed build.
 *
 * <p>The page source is saved alongside the image for the failure mode a picture
 * cannot explain: an element that is present in the DOM but not visible, or a
 * validation message that rendered off-screen.
 *
 * <p>Capture happens in {@link AfterTestExecutionCallback}, which JUnit 5 runs
 * <em>immediately</em> after the test method and <strong>before</strong> any
 * {@code @AfterEach}. The first version of this class used only
 * {@link TestWatcher#testFailed}, which runs after teardown - and since teardown
 * signs the user out, every screenshot was a picture of the login page rather
 * than of the failure. The {@code TestWatcher} hook is kept, but only to print
 * the paths into the build log where Jenkins will show them.
 */
public class ScreenshotOnFailure implements AfterTestExecutionCallback, TestWatcher {

    private static Supplier<WebDriver> driverSupplier = () -> null;

    static void bind(Supplier<WebDriver> supplier) {
        driverSupplier = supplier;
    }

    /** Runs immediately after the test body, before any {@code @AfterEach}. */
    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (context.getExecutionException().isEmpty()) {
            return;
        }
        WebDriver driver = driverSupplier.get();
        if (driver == null) {
            System.err.println("journey failed before the browser started; no screenshot taken");
            return;
        }

        String name = name(context);
        Path image = BaseJourney.screenshot(name);

        try {
            Files.createDirectories(BaseJourney.SCREENSHOT_DIR);
            Path html = BaseJourney.SCREENSHOT_DIR.resolve(name + ".html");
            Files.writeString(html, driver.getPageSource() == null ? "" : driver.getPageSource());

            // The URL at the moment of failure answers the first question anyone
            // asks of a screenshot: which page is this?
            Path note = BaseJourney.SCREENSHOT_DIR.resolve(name + ".txt");
            Files.writeString(note, "url: " + driver.getCurrentUrl() + System.lineSeparator()
                                  + "title: " + driver.getTitle() + System.lineSeparator());
        } catch (Exception e) {
            System.err.println("could not save the page source: " + e.getMessage());
        }

        if (image != null) {
            System.err.println("FAILURE captured: " + image.toAbsolutePath());
            System.err.println("FAILURE url:      " + driver.getCurrentUrl());
        }
    }

    /** Only reports where the evidence went; the capture already happened above. */
    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        System.err.println("FAILURE in " + name(context) + " - evidence under "
                         + BaseJourney.SCREENSHOT_DIR.toAbsolutePath());
    }

    private static String name(ExtensionContext context) {
        return context.getRequiredTestClass().getSimpleName()
             + "." + context.getRequiredTestMethod().getName();
    }
}
