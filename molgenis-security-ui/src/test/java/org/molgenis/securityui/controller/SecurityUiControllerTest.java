package org.molgenis.securityui.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.molgenis.data.security.auth.User;
import org.molgenis.security.user.UserAccountService;
import org.molgenis.settings.AppSettings;
import org.molgenis.web.menu.MenuReaderService;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableWebMvc
public class SecurityUiControllerTest {
  private static final String MENU_PATH_SECURITY_UI = "/menu/path/security-ui";
  public static final String DEFAULT_LANG = "en";
  private MockMvc mockMvc;

  @Mock private MenuReaderService menuReaderService;

  @Mock private AppSettings appSettings;

  @Mock private UserAccountService userAccountService;

  @BeforeEach
  public void before() {
    initMocks(this);

    when(appSettings.getLanguageCode()).thenReturn(DEFAULT_LANG);
    User user = mock(User.class);
    when(userAccountService.getCurrentUser()).thenReturn(user);
    when(user.isSuperuser()).thenReturn(false);
    when(menuReaderService.findMenuItemPath(SecurityUiController.ID))
        .thenReturn(MENU_PATH_SECURITY_UI);

    SecurityUiController securityUiController = new SecurityUiController(menuReaderService);
    mockMvc = standaloneSetup(securityUiController).build();
  }

  @Test
  public void testInit() throws Exception {
    mockMvc
        .perform(get(SecurityUiController.URI))
        .andExpect(status().isOk())
        .andExpect(view().name(SecurityUiController.VIEW_TEMPLATE))
        .andExpect(model().attribute("baseUrl", MENU_PATH_SECURITY_UI));
  }
}
