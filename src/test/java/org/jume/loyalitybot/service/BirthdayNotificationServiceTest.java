package org.jume.loyalitybot.service;

import org.jume.loyalitybot.config.AdminConfig;
import org.jume.loyalitybot.dto.PosterClientDto;
import org.jume.loyalitybot.model.BirthdayGreeting;
import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.model.Customer.CustomerStatus;
import org.jume.loyalitybot.repository.BirthdayGreetingRepository;
import org.jume.loyalitybot.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BirthdayNotificationServiceTest {

    private static final Long POSTER_CLIENT_ID = 849L;
    private static final Long GROUP_WITH_BONUS = 4L;
    private static final Long GROUP_WITHOUT_BONUS = 5L;
    private static final Long TELEGRAM_ID = 6265394382L;
    private static final BigDecimal BIRTHDAY_BONUS = BigDecimal.valueOf(50);

    @Mock
    private PosterApiService posterApiService;
    @Mock
    private TelegramBotService telegramBotService;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private BirthdayGreetingRepository birthdayGreetingRepository;
    @Mock
    private AdminConfig adminConfig;
    @InjectMocks
    private BirthdayNotificationService service;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setId(1L);
        customer.setTelegramId(TELEGRAM_ID);
        customer.setFirstName("Олег");
        customer.setPosterClientId(POSTER_CLIENT_ID);
        customer.setStatus(CustomerStatus.ACTIVE);

        when(posterApiService.getClientGroupBirthdayBonuses())
                .thenReturn(Map.of(GROUP_WITH_BONUS, BIRTHDAY_BONUS, GROUP_WITHOUT_BONUS, BigDecimal.ZERO));
        when(adminConfig.getAdminTelegramIds()).thenReturn(Set.of(100L));
        when(posterApiService.getAllClients()).thenReturn(List.of(clientWithBirthdayToday()));
        when(customerRepository.findByPosterClientId(POSTER_CLIENT_ID)).thenReturn(Optional.of(customer));
        when(birthdayGreetingRepository.saveAndFlush(any(BirthdayGreeting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void reportsGroupBonusWithoutGrantingItAgain() {
        when(birthdayGreetingRepository.existsByCustomerIdAndGreetingYear(eq(1L), anyInt())).thenReturn(false);
        when(posterApiService.getClientBonus(POSTER_CLIENT_ID)).thenReturn(Optional.of(BigDecimal.valueOf(120)));
        when(telegramBotService.sendBirthdayGreeting(eq(TELEGRAM_ID), eq("Олег"), any(), any())).thenReturn(true);

        service.greetBirthdayCustomers();

        // Poster credits the birthday bonus itself — granting it here would double it
        verify(posterApiService, never()).addBonus(anyLong(), any(), anyString());
        verify(telegramBotService).sendBirthdayGreeting(
                eq(TELEGRAM_ID), eq("Олег"), eq(BIRTHDAY_BONUS), eq(BigDecimal.valueOf(120)));

        ArgumentCaptor<BirthdayGreeting> saved = ArgumentCaptor.forClass(BirthdayGreeting.class);
        verify(birthdayGreetingRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(BirthdayGreeting.GreetingStatus.SENT);
        assertThat(saved.getValue().getBonusAmount()).isEqualByComparingTo(BIRTHDAY_BONUS);
    }

    @Test
    void doesNotGreetOrPayTwiceInTheSameYear() {
        when(birthdayGreetingRepository.existsByCustomerIdAndGreetingYear(eq(1L), anyInt())).thenReturn(true);

        service.greetBirthdayCustomers();

        verify(telegramBotService, never()).sendBirthdayGreeting(anyLong(), anyString(), any(), any());
        verify(birthdayGreetingRepository, never()).saveAndFlush(any());
    }

    @Test
    void greetsWithoutGiftLineWhenGroupHasNoBirthdayBonus() {
        when(posterApiService.getAllClients()).thenReturn(List.of(clientInGroup(GROUP_WITHOUT_BONUS)));
        when(birthdayGreetingRepository.existsByCustomerIdAndGreetingYear(eq(1L), anyInt())).thenReturn(false);
        when(telegramBotService.sendBirthdayGreeting(eq(TELEGRAM_ID), eq("Олег"), any(), any())).thenReturn(true);

        service.greetBirthdayCustomers();

        verify(telegramBotService).sendBirthdayGreeting(
                eq(TELEGRAM_ID), eq("Олег"), eq(BigDecimal.ZERO), eq(null));

        ArgumentCaptor<BirthdayGreeting> saved = ArgumentCaptor.forClass(BirthdayGreeting.class);
        verify(birthdayGreetingRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus())
                .isEqualTo(BirthdayGreeting.GreetingStatus.SENT_WITHOUT_BONUS);
    }

    @Test
    void skipsClientsThatAreNotActiveBotCustomers() {
        customer.setStatus(CustomerStatus.PENDING_PHONE);

        service.greetBirthdayCustomers();

        verify(telegramBotService, never()).sendBirthdayGreeting(anyLong(), anyString(), any(), any());
    }

    @Test
    void marksGreetingFailedWhenTelegramRejectsIt() {
        when(birthdayGreetingRepository.existsByCustomerIdAndGreetingYear(eq(1L), anyInt())).thenReturn(false);
        when(posterApiService.getClientBonus(POSTER_CLIENT_ID)).thenReturn(Optional.of(BigDecimal.valueOf(120)));
        when(telegramBotService.sendBirthdayGreeting(anyLong(), anyString(), any(), any())).thenReturn(false);

        service.greetBirthdayCustomers();

        ArgumentCaptor<BirthdayGreeting> saved = ArgumentCaptor.forClass(BirthdayGreeting.class);
        verify(birthdayGreetingRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(BirthdayGreeting.GreetingStatus.FAILED);
        assertThat(saved.getValue().getBonusAmount()).isEqualByComparingTo(BIRTHDAY_BONUS);
    }

    private PosterClientDto clientWithBirthdayToday() {
        return clientInGroup(GROUP_WITH_BONUS);
    }

    private PosterClientDto clientInGroup(Long groupId) {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Kyiv"));
        PosterClientDto client = new PosterClientDto();
        client.setClientId(POSTER_CLIENT_ID);
        client.setFirstName("Олег");
        client.setBirthday(today.minusYears(30).toString());
        client.setClientGroupsId(groupId);
        return client;
    }
}
