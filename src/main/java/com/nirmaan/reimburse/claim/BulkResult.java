package com.nirmaan.reimburse.claim;

import java.util.List;

public record BulkResult(int succeeded, List<String> failures) {
}
