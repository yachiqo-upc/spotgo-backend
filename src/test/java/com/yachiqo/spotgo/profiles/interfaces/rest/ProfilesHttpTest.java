package com.yachiqo.spotgo.profiles.interfaces.rest;

import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.profiles.application.*;
import com.yachiqo.spotgo.profiles.application.internal.ProfilesManagementService;
import com.yachiqo.spotgo.profiles.domain.model.aggregates.*;
import com.yachiqo.spotgo.profiles.domain.model.commands.CreateVehicleCommand;
import com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories.VehicleRepository;
import com.yachiqo.spotgo.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProfilesHttpTest {
    ProfilesManagementService profiles;
    ProfilesCommandService commands;
    MockMvc mvc;
    UUID accountId = UUID.randomUUID();
    SpotgoUserPrincipal actor = new SpotgoUserPrincipal(accountId, "juan@example.com", UserRole.DRIVER, null, null);

    @BeforeEach void setup() {
        profiles = mock(ProfilesManagementService.class);
        commands = mock(ProfilesCommandService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProfilesController(profiles),
                new VehiclesController(commands, mock(ProfilesQueryService.class), mock(VehicleRepository.class), profiles),
                new DriverClassificationsController(profiles))
            .setControllerAdvice(new ProfilesExceptionHandler(), new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.getParameterType() == SpotgoUserPrincipal.class;
                }
                public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                        NativeWebRequest request, WebDataBinderFactory factory) { return actor; }
            }).build();
    }

    @Test void profileCreationReturnsUuidWithoutRequiringIdOrDatesFromFrontend() throws Exception {
        var profile = new Profile(accountId, "Juan", "Perez", "999");
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(profile, "id", id);
        when(profiles.create(actor, "Juan", "Perez", "999")).thenReturn(profile);
        mvc.perform(post("/api/v1/profiles").contentType(MediaType.APPLICATION_JSON)
            .content("{\"firstName\":\"Juan\",\"lastName\":\"Perez\",\"phone\":\"999\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.accountId").value(accountId.toString()));
    }

    @Test void blankProfileFieldsReturn400() throws Exception {
        mvc.perform(post("/api/v1/profiles").contentType(MediaType.APPLICATION_JSON)
            .content("{\"firstName\":\"  \",\"lastName\":\"Perez\",\"phone\":\"999\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(profiles);
    }

    @Test void absentProfileReturns404RatherThan500() throws Exception {
        when(profiles.current(actor)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Create your profile first."));
        mvc.perform(get("/api/v1/profiles/me")).andExpect(status().isNotFound());
    }

    @Test void malformedVehicleUuidReturns400() throws Exception {
        mvc.perform(patch("/api/v1/vehicles/not-a-uuid").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(commands);
    }

    @Test void invalidClassificationEnumReturns400() throws Exception {
        mvc.perform(put("/api/v1/driver-classifications/tenants/" + UUID.randomUUID() + "/profiles/" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON).content("{\"profileType\":\"ADMIN\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(profiles);
    }

    @Test void addingTwoVehiclesUsesAuthenticatedProfileBothTimes() throws Exception {
        var profile = new Profile(accountId, "Juan", "Perez", "999");
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(profile, "id", id);
        when(profiles.driver(actor)).thenReturn(profile);
        when(commands.handle(any(CreateVehicleCommand.class))).thenAnswer(invocation -> {
            CreateVehicleCommand c = invocation.getArgument(0);
            org.junit.jupiter.api.Assertions.assertEquals(id, c.profileId());
            return Optional.of(new Vehicle(profile, c.plate(), c.make(), c.model(), c.color()));
        });
        for (String plate : List.of("ABC-123", "DEF-456")) {
            mvc.perform(post("/api/v1/vehicles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plate\":\"" + plate + "\",\"make\":\"Kia\",\"model\":\"Rio\",\"color\":\"Blue\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.profileId").value(id.toString()));
        }
        verify(commands, times(2)).handle(any(CreateVehicleCommand.class));
    }
}
