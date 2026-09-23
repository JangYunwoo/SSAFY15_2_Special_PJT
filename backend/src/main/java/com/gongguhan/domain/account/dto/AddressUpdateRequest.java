package com.gongguhan.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** USR-05: deliveryRequest 생략은 유지, 명시적 null은 삭제. 다른 null 필드는 기존 계약을 유지한다. */
public class AddressUpdateRequest {
    @JsonProperty @Size(max = 50) private String recipientName;
    @JsonProperty @Size(max = 20)
    @Pattern(regexp = AccountInputPatterns.PHONE_DIGITS, message = AccountInputPatterns.PHONE_MESSAGE)
    private String recipientPhone;
    @JsonProperty @Pattern(regexp = "\\d{5}", message = "우편번호는 5자리 숫자여야 합니다.") private String zipcode;
    @JsonProperty @Size(max = 255) private String address1;
    @JsonProperty @Size(max = 255) private String address2;
    @Size(max = 100) private String deliveryRequest;
    @JsonProperty private Boolean isDefault;
    @JsonIgnore private boolean deliveryRequestPresent;

    @JsonCreator
    public AddressUpdateRequest() { }

    /** 기존 Java 호출의 null=미변경 계약을 유지한다. HTTP null 삭제는 setter가 구별한다. */
    public AddressUpdateRequest(String recipientName, String recipientPhone, String zipcode,
                                String address1, String address2, String deliveryRequest, Boolean isDefault) {
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipcode = zipcode;
        this.address1 = address1;
        this.address2 = address2;
        this.deliveryRequest = deliveryRequest;
        this.deliveryRequestPresent = deliveryRequest != null;
        this.isDefault = isDefault;
    }

    @JsonSetter("deliveryRequest")
    public void setDeliveryRequest(String value) {
        deliveryRequestPresent = true;
        deliveryRequest = value;
    }

    public String recipientName() { return recipientName; }
    public String recipientPhone() { return recipientPhone; }
    public String zipcode() { return zipcode; }
    public String address1() { return address1; }
    public String address2() { return address2; }
    public String deliveryRequest() { return deliveryRequest; }
    public Boolean isDefault() { return isDefault; }
    public boolean hasDeliveryRequest() { return deliveryRequestPresent; }
}
