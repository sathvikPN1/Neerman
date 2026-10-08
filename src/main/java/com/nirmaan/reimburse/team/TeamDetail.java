package com.nirmaan.reimburse.team;

import java.util.List;

public record TeamDetail(TeamSummary summary, List<Member> members) {

    public record Member(Long userId, String name, String email, boolean active, boolean invitePending) {
    }
}
