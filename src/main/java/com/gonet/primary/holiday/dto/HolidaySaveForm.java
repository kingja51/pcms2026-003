package com.gonet.primary.holiday.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class HolidaySaveForm {

    private String holidayId;

    @NotNull
    private LocalDate holidayDate;

    @NotBlank
    @Size(max = 100)
    private String holidayName;

    @NotBlank
    @Pattern(regexp = "^(PUBLIC|COMPANY|MEMORIAL|OTHER)$",
             message = "holiday_type 는 PUBLIC/COMPANY/MEMORIAL/OTHER 중 하나")
    private String holidayType = "PUBLIC";

    @Size(max = 500)
    private String description;

    private String useYn;

    /** holiday_date 의 YEAR 값 — Service 단에서 자동 채움 */
    public int resolveYear() {
        return holidayDate != null ? holidayDate.getYear() : 0;
    }

    public static String yn(String raw) {
        return "Y".equalsIgnoreCase(raw) ? "Y" : "N";
    }
}
