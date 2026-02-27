package org.jume.loyalitybot.dto.admin;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TelegramLoginData {
    private Long id;
    private String firstName;
    private String lastName;
    private String username;
    private String photoUrl;
    private Long authDate;
    private String hash;

    public String getDisplayName() {
        if (firstName != null && !firstName.isBlank()) {
            return lastName != null && !lastName.isBlank()
                ? firstName + " " + lastName
                : firstName;
        }
        return username != null ? "@" + username : "User " + id;
    }
}
