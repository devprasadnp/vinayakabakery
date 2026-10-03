package com.harish.pickles;

import com.harish.pickles.config.AppProperties;
import com.harish.pickles.config.GoogleMapsProperties;
import com.harish.pickles.config.MailImapConnectionProperties;
import com.harish.pickles.payment.PaymentProperties;
import com.harish.pickles.payment.PaytmProperties;
import com.harish.pickles.payment.PhonePeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties({AppProperties.class, PhonePeProperties.class, PaymentProperties.class,
        PaytmProperties.class, MailImapConnectionProperties.class, GoogleMapsProperties.class})
public class PicklesApplication {

    public static void main(String[] args) {
        SpringApplication.run(PicklesApplication.class, args);
    }
}
