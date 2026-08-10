package msb.com.vn.dsign.hsm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing information about a single HSM slot.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HsmSlotInfo {

    /**
     * Unique identifier for the HSM slot.
     */
    private String slotId;

    /**
     * Whether the slot is currently available for operations.
     */
    private boolean available;
}
