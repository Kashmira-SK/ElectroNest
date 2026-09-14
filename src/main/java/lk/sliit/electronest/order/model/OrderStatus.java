package lk.sliit.electronest.order.model;

import java.util.EnumSet;
import java.util.Set;


public enum OrderStatus {

    PENDING {
        @Override
        public Set<OrderStatus> allowedNextStatuses() {
            return EnumSet.of(PROCESSING, CANCELLED);
        }
    },
    PROCESSING {
        @Override
        public Set<OrderStatus> allowedNextStatuses() {
            return EnumSet.of(DELIVERED, CANCELLED);
        }
    },
    DELIVERED {
        @Override
        public Set<OrderStatus> allowedNextStatuses() {
            return EnumSet.noneOf(OrderStatus.class); // only via admin override
        }
    },
    CANCELLED {
        @Override
        public Set<OrderStatus> allowedNextStatuses() {
            return EnumSet.noneOf(OrderStatus.class); // terminal
        }
    };

    public abstract Set<OrderStatus> allowedNextStatuses();

    public boolean canTransitionTo(OrderStatus target) {
        return allowedNextStatuses().contains(target);
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
