package org.jp.illg.dstar.repeater;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.defines.RepeaterTypes;
import org.jp.illg.dstar.repeater.model.DStarRepeaterEvent;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DSTARRepeaterFactory {

	private DSTARRepeaterFactory() {}

	public static DSTARRepeater createRepeater(
		@NonNull final UUID systemID,
		@NonNull final DSTARGateway gateway,
		@NonNull final RepeaterTypes repeaterType, @NonNull final String repeaterCallsign,
		@NonNull final ExecutorService workerExecutor,
		final EventListener<DStarRepeaterEvent> eventListener,
		final SocketIO socketIO
	) {
		if("".equals(repeaterCallsign)) {return null;}

		if(!CallSignValidator.isValidRepeaterCallsign(repeaterCallsign)) {
			if(log.isWarnEnabled())
				log.warn("Could not load Repeater " + repeaterCallsign + ", because illegal callsign.");

			return null;
		}

		DSTARRepeater repeater = null;

		repeater = DSTARRepeaterManager.getRepeater(systemID, repeaterCallsign);

		if(repeater != null) {
			if(log.isWarnEnabled()) {
				log.warn(
					"Could not create repeater " + repeaterCallsign +
					", because already assigned same repeater callsign."
				);
			}

			return null;
		}

		if(socketIO != null) {
			repeater = createRepeaterInstance(
				systemID,
				gateway,
				repeaterType,
				repeaterCallsign,
				workerExecutor,
				eventListener,
				socketIO
			);
		}
		else {
			repeater = createRepeaterInstance(
				systemID,
				gateway,
				repeaterType,
				repeaterCallsign,
				workerExecutor,
				eventListener
			);
		}

		return repeater;
	}

	private static DSTARRepeater createRepeaterInstance(
		@NonNull final UUID systemID,
		@NonNull DSTARGateway gateway,
		@NonNull RepeaterTypes repeaterType, @NonNull final String repeaterCallsign,
		@NonNull ExecutorService workerExecutor,
		final EventListener<DStarRepeaterEvent> eventListener
	) {
		return createRepeaterInstance(
			systemID,
			gateway,
			repeaterType,
			repeaterCallsign,
			workerExecutor,
			eventListener,
			null
		);
	}

	private static DSTARRepeater createRepeaterInstance(
		@NonNull final UUID systemID,
		@NonNull DSTARGateway gateway,
		@NonNull RepeaterTypes repeaterType, @NonNull final String repeaterCallsign,
		@NonNull ExecutorService workerExecutor,
		final EventListener<DStarRepeaterEvent> eventListener,
		SocketIO socketIO
	) {
		DSTARRepeater repeater = null;
		try {
			@SuppressWarnings("unchecked")
			final Class<? extends DSTARRepeater> repeaterClass =
				(Class<? extends DSTARRepeater>)Class.forName(repeaterType.getClassName());

			if(socketIO != null) {
				final Constructor<? extends DSTARRepeater> constructor =
					repeaterClass.getConstructor(
						UUID.class,
						DSTARGateway.class,
						String.class,
						ExecutorService.class,
						EventListener.class,
						SocketIO.class
					);

				repeater = constructor.newInstance(
					systemID, gateway, repeaterCallsign, workerExecutor, eventListener, socketIO
				);
			}
			else {
				final Constructor<? extends DSTARRepeater> constructor =
					repeaterClass.getConstructor(
						UUID.class,
						DSTARGateway.class,
						String.class,
						ExecutorService.class,
						EventListener.class
					);

				repeater = constructor.newInstance(
					systemID, gateway, repeaterCallsign, workerExecutor, eventListener
				);
			}

		}catch(
			ClassNotFoundException |
			ClassCastException |
			NoSuchMethodException |
			SecurityException |
			InstantiationException |
			IllegalAccessException |
			IllegalArgumentException |
			InvocationTargetException ex
		) {
			if(log.isWarnEnabled())
				log.warn("Could not load Repeater " + repeaterCallsign + " type " + repeaterType + ".", ex);
		}

		return repeater;
	}
}
