package com.project.shopapp.dtos;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class RefreshTokenDTO {
    @NotBlank
    private String refreshToken;
}
