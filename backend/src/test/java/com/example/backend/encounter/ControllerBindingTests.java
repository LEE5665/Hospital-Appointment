package com.example.backend.encounter;

import com.example.backend.encounter.controller.ClinicController;
import com.example.backend.encounter.service.ClinicService;
import com.example.backend.encounter.dto.SoapInput;
import com.example.backend.appointment.controller.AppointmentController;
import com.example.backend.appointment.service.AppointmentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ControllerBindingTests {
    @Test void bindsPatientHistoryAndPage() throws Exception {
        var service = mock(ClinicService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ClinicController(service)).build();
        mvc.perform(get("/api/clinic/patients/17/encounters").param("page","2")).andExpect(status().isOk());
        verify(service).history(17L,2);
        mvc.perform(get("/api/clinic/patients/17/encounters")).andExpect(status().isOk());
        verify(service).history(17L,0);
    }
    @Test void bindsPrescriptionBodyAndRejectsMissingOrInvalidItems() throws Exception {
        var service = mock(ClinicService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ClinicController(service)).build();
        mvc.perform(put("/api/clinic/encounters/42/prescriptions").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"version":3,"prescriptions":[{"medicationCode":"8800000000001","dose":0.5,"unit":"정","frequency":3,"days":5,"instructions":"식후"}]}
                """)).andExpect(status().isOk());
        verify(service).savePrescriptions(eq(42L), argThat(input -> input.version() == 3
            && input.prescriptions().size() == 1 && input.prescriptions().getFirst().dose().toPlainString().equals("0.5")), isNull());
        mvc.perform(put("/api/clinic/encounters/42/prescriptions").contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":3}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/clinic/encounters/42/prescriptions").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"version":3,"prescriptions":[{"medicationCode":"8800000000001","dose":0,"unit":"정","frequency":0,"days":0,"instructions":""}]}
                """)).andExpect(status().isBadRequest());
    }
    @Test void bindsPatientSearchAndQueueParameters() throws Exception {
        var service = mock(ClinicService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ClinicController(service)).build();
        mvc.perform(get("/api/clinic/patients").param("query", "Kim")).andExpect(status().isOk());
        verify(service).patients("Kim");
        mvc.perform(get("/api/clinic/encounters/queue")
            .param("filter", "COMPLETED").param("mine", "true").param("page", "2").param("size", "10"))
            .andExpect(status().isOk());
        verify(service).queue(null, "COMPLETED", true, 2, 10, null);
    }

    @Test void bindsEncounterIdAndSoapBody() throws Exception {
        var service = mock(ClinicService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ClinicController(service)).build();
        mvc.perform(put("/api/clinic/encounters/42/soap").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"subjective":"S","objective":"O","assessment":"A","plan":"P","complete":false,"version":3,"diagnoses":[]}
                """))
            .andExpect(status().isOk());
        verify(service).save(42L, new SoapInput("S", "O", "A", "P", false, 3), null);
    }

    @Test void bindsAppointmentPatientIdAndOptionalDate() throws Exception {
        var service = mock(AppointmentService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AppointmentController(service)).build();
        mvc.perform(get("/api/clinic/appointments/patient/17/today")).andExpect(status().isOk());
        verify(service).todayForPatient(17L);
        mvc.perform(get("/api/clinic/appointments")).andExpect(status().isOk());
        verify(service).list(null);
    }
}
