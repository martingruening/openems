package io.openems.edge.controller.ess.delayedselltogrid;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.power.api.Power;
import io.openems.edge.meter.api.SymmetricMeter;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Ess.DelayedSellToGrid", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class DelayedSellToGridImpl extends AbstractOpenemsComponent
		implements DelayedSellToGrid, Controller, OpenemsComponent {

	private final Logger log = LoggerFactory.getLogger(DelayedSellToGridImpl.class);

	@Reference
	protected ComponentManager componentManager;

	@Reference
	protected ConfigurationAdmin cm;

	@Reference
	protected Power power;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	private ManagedSymmetricEss ess;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	private SymmetricMeter meter;

	private Config config;
	private int calculatedPower;

	public DelayedSellToGridImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				DelayedSellToGrid.ChannelId.values() //
		);
	}

	@Activate
	void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "meter", config.meter_id())) {
			return;
		}
		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "ess", config.ess_id())) {
			return;
		}
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run() throws OpenemsNamedException {
		ManagedSymmetricEss ess = this.componentManager.getComponent(this.config.ess_id());
		SymmetricMeter meter = this.componentManager.getComponent(this.config.meter_id());

		/*
		 * Check that we are On-Grid (and warn on undefined Grid-Mode)
		 */
		GridMode gridMode = ess.getGridMode();
		if (gridMode.isUndefined()) {
			this.logWarn(this.log, "Grid-Mode is [UNDEFINED]");
		}
		switch (gridMode) {
		case ON_GRID:
		case UNDEFINED:
			break;
		case OFF_GRID:
			return;
		}

		// Calculate 'real' grid-power (without current ESS charge/discharge)
		int gridPower = meter.getActivePower().orElse(0);

		if (-gridPower > this.config.sellToGridPowerLimit()) {
			/*
			 * Exceeds the Sell To Grid Power Limit
			 */
			calculatedPower = this.config.sellToGridPowerLimit() + gridPower
					- ess.getActivePower().orElse(0);/* current charge/discharge Ess */

		} else if (-gridPower < this.config.continuousSellToGridPower()) {
			/*
			 * Continuous Sell To Grid
			 */
			calculatedPower = this.config.continuousSellToGridPower() + gridPower
					+ ess.getActivePower().orElse(0) /* current charge/discharge Ess */;

		} else if (-gridPower >= this.config.continuousSellToGridPower() || -gridPower <= this.config.continuousSellToGridPower()) {
			/*
			 * In Between 
			 */
			calculatedPower = ess.getActivePower().orElse(0) /* current charge/discharge Ess */;
		} else {

			/*
			 * Do nothing
			 */
			// TODO DO Nothing Or Set zero ?
//			calculatedPower = 0;
		}

		/*
		 * set result
		 */
		ess.setActivePowerEquals(calculatedPower);
		ess.setReactivePowerEquals(0);
	}
}
