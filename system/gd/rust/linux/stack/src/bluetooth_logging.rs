use bt_common::init_flags;
use log::LevelFilter;
use syslog::{BasicLogger, Error, Facility, Formatter3164};

use log_panics;

/// API to modify log levels.
pub trait IBluetoothLogging {
    /// Check whether debug logging is enabled.
    fn is_debug_enabled(&self) -> bool;

    /// Change whether debug logging is enabled.
    fn set_debug_logging(&mut self, enabled: bool);
}

/// Logging related implementation.
pub struct BluetoothLogging {
    is_debug: bool,
    is_stderr: bool,
    is_initialized: bool,
}

impl BluetoothLogging {
    pub fn new(is_debug: bool, log_output: &str) -> Self {
        let is_stderr = log_output == "stderr";
        Self { is_debug, is_stderr, is_initialized: false }
    }

    pub fn initialize(&mut self) -> Result<(), Error> {
        let level = if self.is_debug { LevelFilter::Debug } else { LevelFilter::Info };

        if self.is_stderr {
            env_logger::Builder::new().filter(None, level).init();
        } else {
            let formatter = Formatter3164 {
                facility: Facility::LOG_USER,
                hostname: None,
                process: "btadapterd".into(),
                pid: 0,
            };

            let logger = syslog::unix(formatter)?;
            let _ = log::set_boxed_logger(Box::new(BasicLogger::new(logger)))
                .map(|()| log::set_max_level(level));
            log_panics::init();
        }
        self.is_initialized = true;
        Ok(())
    }
}

impl IBluetoothLogging for BluetoothLogging {
    fn is_debug_enabled(&self) -> bool {
        self.is_initialized && self.is_debug
    }

    fn set_debug_logging(&mut self, enabled: bool) {
        if !self.is_initialized {
            return;
        }

        self.is_debug = enabled;

        // Update log level in Linux stack.
        let level = if self.is_debug { LevelFilter::Debug } else { LevelFilter::Info };
        log::set_max_level(level);

        // Update log level in libbluetooth.
        let level =
            if self.is_debug { init_flags::LOG_TAG_DEBUG } else { init_flags::LOG_TAG_INFO };
        init_flags::update_default_log_level(level);

        // Mark the start of debug logging with a debug print.
        if self.is_debug {
            log::debug!("Debug logging successfully enabled!");
        }

        log::info!("Setting debug logging to {}", self.is_debug);
    }
}
