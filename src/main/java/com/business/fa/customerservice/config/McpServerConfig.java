package com.business.fa.customerservice.config;

import com.business.fa.customerservice.function.CustomerService;
import com.business.fa.customerservice.function.RefundService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration("customerServiceMcp")
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider customerServiceTools(CustomerService customerService){
        return MethodToolCallbackProvider.builder().toolObjects(customerService).build();
    }

    @Bean
    public ToolCallbackProvider refundServiceTools(RefundService refundService){
        return MethodToolCallbackProvider.builder().toolObjects(refundService).build();
    }
}
