package com.jmeter.suite.notify;

import com.jmeter.suite.report.ReportArtifactPaths;
import com.jmeter.suite.util.Log;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.nio.file.Files;
import java.util.Properties;

/**
 * Emails a run's report archive when SMTP settings are present in the environment.
 *
 * <p>Credentials come from environment variables rather than the checked-in properties files, so
 * they are never committed. Delivery is best-effort: a mail failure is reported but must not fail a
 * run whose results are already on disk.
 */
public final class ReportMailer {

    /**
     * Prevents instantiation of this utility type.
     */
    private ReportMailer() {
    }

    /**
     * Sends the report archive, skipping quietly when SMTP is not configured.
     */
    public static void sendIfConfigured(String planName, ReportArtifactPaths artifacts) {
        String smtpHost = env("SMTP_HOST", null);
        String to = env("SMTP_TO", null);
        if (smtpHost == null || to == null) {
            Log.info("Email skipped: SMTP_HOST/SMTP_TO not set.");
            return;
        }

        String smtpUser = env("SMTP_USER", null);
        String smtpPass = env("SMTP_PASS", null);

        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", env("SMTP_PORT", "587"));
        props.put("mail.smtp.starttls.enable", env("SMTP_STARTTLS", "true"));
        props.put("mail.smtp.auth", smtpUser != null ? "true" : "false");

        try {
            Session session = buildSession(props, smtpUser, smtpPass);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(
                    env("SMTP_FROM", smtpUser != null ? smtpUser : "jmeter@localhost")));
            for (String address : to.split(",")) {
                if (!address.trim().isEmpty()) {
                    message.addRecipient(Message.RecipientType.TO, new InternetAddress(address.trim()));
                }
            }
            message.setSubject("JMeter report: " + planName);
            message.setContent(buildBody(planName, artifacts));

            Transport.send(message);
            Log.info("Email sent to: " + to);
        } catch (Exception ex) {
            Log.info("Email send failed: " + ex.getMessage());
        }
    }

    /**
     * Builds the message body, attaching the report archive when it exists.
     */
    private static MimeMultipart buildBody(String planName, ReportArtifactPaths artifacts) throws Exception {
        MimeBodyPart text = new MimeBodyPart();
        text.setText("Attached: JMeter report for plan '" + planName + "'.\n"
                + "JTL: " + artifacts.jtlPath().toAbsolutePath() + "\n"
                + "HTML: " + artifacts.htmlDir().toAbsolutePath() + "\n");

        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(text);

        if (Files.exists(artifacts.zipPath())) {
            MimeBodyPart attachment = new MimeBodyPart();
            attachment.attachFile(artifacts.zipPath().toFile());
            attachment.setFileName(artifacts.zipPath().getFileName().toString());
            multipart.addBodyPart(attachment);
        }
        return multipart;
    }

    /**
     * Builds a mail session, authenticating only when credentials are supplied.
     */
    private static Session buildSession(Properties props, String smtpUser, String smtpPass) {
        if (smtpUser != null && smtpPass != null) {
            return Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUser, smtpPass);
                }
            });
        }
        return Session.getInstance(props);
    }

    /**
     * Reads an environment variable with a fallback.
     */
    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }
}
