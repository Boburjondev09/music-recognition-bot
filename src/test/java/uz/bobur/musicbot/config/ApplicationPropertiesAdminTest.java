package uz.bobur.musicbot.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationPropertiesAdminTest {

    @Test
    void shouldRecognizeConfiguredAdminIds() {
        ApplicationProperties.Admin admin = new ApplicationProperties.Admin("647292785, 111222333");

        assertThat(admin.isAdmin(647292785L)).isTrue();
        assertThat(admin.isAdmin(111222333L)).isTrue();
        assertThat(admin.isAdmin(999999999L)).isFalse();
    }

    @Test
    void shouldTreatBlankOrNullAsNoAdmins() {
        assertThat(new ApplicationProperties.Admin("").isAdmin(647292785L)).isFalse();
        assertThat(new ApplicationProperties.Admin(null).isAdmin(647292785L)).isFalse();
    }
}
