package com.thaishopfun.customer;

import java.util.List;

/** 409 DUPLICATE_CUSTOMER: another live customer of the shop has the same normalized phone or email. */
public class DuplicateCustomerException extends RuntimeException {
    public static final String CODE = "DUPLICATE_CUSTOMER";
    private final List<CustomerDtos.DuplicateMatch> matches;

    public DuplicateCustomerException(List<CustomerDtos.DuplicateMatch> matches) {
        super("มีลูกค้าที่ใช้เบอร์โทรหรืออีเมลนี้อยู่แล้ว ใช้รายการเดิม รวมลูกค้า หรือส่ง allowDuplicate=true เพื่อบันทึกแยก");
        this.matches = List.copyOf(matches);
    }

    public List<CustomerDtos.DuplicateMatch> getMatches() { return matches; }
}
