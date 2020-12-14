package io.openems.edge.battery.bydcommercial.statemachine;

import java.time.Duration;
import java.time.Instant;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.bydcommercial.BatteryBoxC130;
import io.openems.edge.battery.bydcommercial.PowerCircuitControl;
import io.openems.edge.battery.bydcommercial.statemachine.StateMachine.State;
import io.openems.edge.common.statemachine.StateHandler;

public class ErrorHandler extends StateHandler<State, Context> {

	private Instant entryAt = Instant.MIN;

	@Override
	protected void onEntry(Context context) throws OpenemsNamedException {
		this.entryAt = Instant.now();

		// Try to stop system
		BatteryBoxC130 battery = context.getParent();
		battery._setPowerCircuitControl(PowerCircuitControl.SWITCH_OFF);
	}

	@Override
	protected void onExit(Context context) throws OpenemsNamedException {
		BatteryBoxC130 battery = context.getParent();
		battery._setMaxStartAttempts(false);
		battery._setMaxStopAttempts(false);
	}

	@Override
	public State runAndGetNextState(Context context) {
		if (Duration.between(this.entryAt, Instant.now()).getSeconds() > 120) {
			// Try again
			return State.UNDEFINED;
		}

		return State.ERROR;
	}

}
