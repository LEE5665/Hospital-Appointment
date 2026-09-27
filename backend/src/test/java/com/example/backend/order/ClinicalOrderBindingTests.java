package com.example.backend.order;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClinicalOrderBindingTests {
    @Test void bindsCreationFiltersAndActionsAndRejectsInvalidInput() throws Exception {
        var service=mock(ClinicalOrderService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new ClinicalOrderController(service)).build();
        mvc.perform(post("/api/clinic/orders").contentType(MediaType.APPLICATION_JSON)
            .content("{\"encounterId\":1,\"itemId\":2,\"instructions\":\"검사 요청\"}"))
            .andExpect(status().isCreated());
        verify(service).create(new ClinicalOrderService.Create(1L,2L,"검사 요청"),null);
        mvc.perform(post("/api/clinic/orders").contentType(MediaType.APPLICATION_JSON)
            .content("{\"encounterId\":1,\"type\":\"TEST\",\"itemName\":\"직접 입력 검사\",\"instructions\":\"\"}"))
            .andExpect(status().isCreated());
        verify(service).create(new ClinicalOrderService.Create(1L,null,"","직접 입력 검사",OrderItem.Type.TEST),null);
        mvc.perform(get("/api/clinic/orders").param("patientId","5").param("type","TEST").param("unreviewed","true"))
            .andExpect(status().isOk());
        verify(service).list(null,null,5L,OrderItem.Type.TEST,"ALL",false,true,0,null);
        for (String action : new String[]{"start","complete","cancel","review"})
            mvc.perform(post("/api/clinic/orders/7/"+action).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":3,\"text\":\"내용\"}")).andExpect(status().isOk());
        verify(service).complete(7L,new ClinicalOrderService.Change(3,"내용"),null);
        mvc.perform(post("/api/clinic/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/clinic/orders/7/start").contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":-1,\"text\":\"\"}")).andExpect(status().isBadRequest());
    }
}
