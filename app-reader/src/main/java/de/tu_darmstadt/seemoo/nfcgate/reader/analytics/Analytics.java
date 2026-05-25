package de.tu_darmstadt.seemoo.nfcgate.reader.analytics;

/**
 * Analytics abstraction layer. Implementations can integrate with Firebase,
 * custom backends, or remain no-op for privacy.
 */
public interface Analytics {
    void logEvent(String name, android.os.Bundle params);
    void setUserProperty(String key, String value);

    /**
     * Default no-op implementation for privacy-first deployments.
     */
    Analytics NO_OP = new Analytics() {
        @Override
        public void logEvent(String name, android.os.Bundle params) { }

        @Override
        public void setUserProperty(String key, String value) { }
    };
}
