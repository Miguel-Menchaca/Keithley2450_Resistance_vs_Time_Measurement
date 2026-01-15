import sys
import os
import threading
import time
import json
from time import sleep, time as now
from typing import Any
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from matplotlib.ticker import EngFormatter


from pymeasure.experiment import Procedure, IntegerParameter, FloatParameter
from pymeasure.experiment import Results, Worker
from pymeasure.instruments.keithley import Keithley2450

# Initialize a list to store data 
data = []

# get parameters from java GUI
Voltage = float(sys.argv[1])
Time = float(sys.argv[2])
sample_interval_arg = sys.argv[3] # "AUTO" OR numeric string
current_range_arg = sys.argv[4]   # "AUTO" or numeric string
nplc_param = float(sys.argv[5])
compliance_curr = float(sys.argv[6])
output_folder = sys.argv[7]
output_filename = sys.argv[8]
output_path = f"{output_folder}/{output_filename}"
terminal = sys.argv[9]  # "REAR" or "FRONT" terminals


# ---- Logging ----
import logging
logging.basicConfig(level=logging.INFO)
log = logging.getLogger(__name__)


class ResistanceMeasurementProcedure(Procedure):
    # Measurement Parameters
    applied_voltage = FloatParameter("Applied voltage (V)", default=Voltage)
    applied_time = FloatParameter("Applied time (s)", default=Time)
    compliance = FloatParameter("Compliance current (A)", default =compliance_curr)


    def _listen_for_stop(self):
        for line in sys.stdin:
           if line.strip().upper() == "STOP":
               self.stop_requested = True
               break

    def startup(self):
        self.stop_requested = False

        # Detect and connect to Instrument
        import pyvisa
        rm = pyvisa.ResourceManager()
        resources = rm.list_resources()

        print("Avaliable resources:", resources)
        keithley_resources = [r for r in resources if '0x05E6::0x2450' in r]
        print(f"Keithley candidates: {keithley_resources}") 
        if not keithley_resources:
            print("ERROR: No Keithley 2450 found!")
           # log.error(f"No Keithley 2450 found. Detected: {resources}")
            raise ConnectionError(f"No Keithley 2450 found. Detected: {resources}")
        
        self.instrument = Keithley2450(keithley_resources[0])
        print(f"Connected to: {keithley_resources[0]}")
        #log.info(f"Connected to: {keithley_resources[0]}")
        self.instrument.reset()
        #self.instrument = Keithley2450("USB0::0x05E6::0x2450::04436600::INSTR") # La keithley de la estacion de mediciones 
        #self.instrument = Keithley2450("USB0::0x05E6::0x2450::04081967::INSTR")  # La keithley viejita
        

        # Compute the sampling interval 
        if isinstance(sample_interval_arg, str) and sample_interval_arg.upper() == "AUTO":
            # Automatiac mode: compute from NPLC
            mains_frequency = 60.0 
            self.integration_time = nplc_param / mains_frequency
            self.sample_int = self.integration_time #* 1.2
            
           # log.info(f"Automatic sample interval selected: {self.sample_int:.6f} s"
               #      f"(from NPLC={nplc_param})")
        else:
            # Manual mode
            try:
                self.sample_int = float(sample_interval_arg)
                print(f"Manual sample interval selected: {self.sample_int:.6f} s")
                #log.info(f"Manual sample interval selected: {self.sample_int:.6f} s")
            except:
                print("Invalid sample interval argument. Falling back to AUTO mode.")
                #log.error("Invalid sample interval argument. Falling back to AUTO mode.")
                mains_frequency = 60.0
                self.integration_time = nplc_param / mains_frequency
                self.sample_int = self.integration_time #* 1.2
        
     
        # Use rear or front terminals depending on which was selected

        if isinstance(terminal, str) and terminal.upper() == "REAR":
            self.instrument.use_rear_terminals()
        elif isinstance(terminal, str) and terminal.upper() == "FRONT":
            self.instrument.use_front_terminals()
        
        # Explicitly configure for 4-wire measurements
        self.instrument.write(":SENSE:FUNC 'CURR'")  # Set to measure current
        self.instrument.write(":SYSTEM:RSENSE ON")   # Enable 4-wire sense mode
        self.instrument.write(":DISPLAY:MEASURE:FUNCTION CURRENT")  # Display current measurement
        
        # Configure voltage source
        self.instrument.apply_voltage()
        self.instrument.compliance_current = self.compliance
        self.instrument.source_voltage = 0

        # Improve measurement stability
        self.instrument.write(f":SENSE:CURRENT:NPLC {nplc_param}")  # Higher accuracy (number of power line cycles)
        self.instrument.write(":SENSE:CURRENT:AZERO OFF")  # Disable auto-zero for speed
        self.instrument.write(":SENSE:CURRENT:AVERAGE:STATE OFF")  # No averaging
        

        # Set current range: if user provided "AUTO" set autorange, else set numeric range
        if isinstance(current_range_arg, str) and current_range_arg.upper() == "AUTO":
            try:
                # Instrument SCPI for autorange is typically :SENSE:CURRENT:RANGE:AUTO ON
                self.instrument.write(":SENSE:CURRENT:RANGE:AUTO ON")
            except Exception:
                print("Could not set autorange using SCPI - continuing (driver may auto-range).")
               # log.warning("Could not set autorange using SCPI - continuing (driver may auto-range).")
        else:
            try:
                # numeric range in amperes
                range_val = float(current_range_arg)
                # SCPI to set range: :SENSE:CURRENT:RANGE <value>
                self.instrument.write(f":SENSE:CURRENT:RANGE {range_val}")
            except Exception:
                print("Invalid current range argument; leaving instrument in default range or auto-range.")
                #log.warning("Invalid current range argument; leaving instrument in default range or auto-range.")

        self.instrument.enable_source()
        print("Keithley 2450 initialized in 4-wire mode using {terminal} terminals")
       # log.info("Keithley 2450 initialized in 4-wire mode using {terminal} terminals")

         # Start STOP listener thread
        stop_thread = threading.Thread(target=self._listen_for_stop, daemon=True)
        stop_thread.start()


    def shutdown(self):
        #
        try:
            self.instrument.source_voltage = 0
            self.instrument.disable_source()
        except Exception as e:
            print("Error while shutting down instrument: %s", e)
          	# log.exception("Error while shutting down instrument: %s", e)
           	#print("Test completed and instrument output disabled.")
        	#log.info("Test completed and instrument output disabled.")
    
    def should_stop(self):
        return self.stop_requested
    
    def _measure_loop(self, hold_voltage: float, hold_time: float, stage_name: str):
        # This function holds the source at the "hold_voltage" for "hold_time" seconds,
        # and measures current every "sample_interval". Calculates resistance.
        # Appends samples to global "data".

        self.instrument.source_voltage = hold_voltage
        #sleep(self.integration_time)  # small time to settle the voltage
        t0 = now()
        end_time = t0 + hold_time
        next_sample = t0

        while now() < end_time:
            
            if self.should_stop():
                print(f"STOP requested by user - exiting measurement loop.")
               # log.info(f"STOP requested by user - exiting measurement loop.")
                break

            current_time = now()
            #read current from instrument driver
            try:
                i_meas = self.instrument.current
                if abs(hold_voltage) > 1e-6 and abs(i_meas) > 1e-12:
                    res = abs(hold_voltage/i_meas)
                else:
                    res = float('nan')
            except Exception as e:
                print(f"Failed to read current: %s", e)
                #log.exception("Failed to read current: %s", e)
                i_meas = float('nan')
                res = float('nan')
            
            elapsed_stage = current_time - t0
            elapsed_total = current_time - start_time_global

            data.append({
                #"stage": stage_name,
                #"stage_time_s": elapsed_stage,
                "abs_time_s": elapsed_total,
                "voltage_V": hold_voltage,
                "current_A": i_meas,
                "resistance_Ohm": res
            })

            # stream data to stdout for Java GUI (one line per sample)
            # Format: STAGE, abs_time_s, stage_time_s, voltage, current
            print(f"{elapsed_total:.6f},{hold_voltage:.6f},"
                  f"{i_meas:.12e}, {res:.12e}")
            sys.stdout.flush()

            # sleep until next sample - sample approach
            next_sample += self.sample_int
            sleep(max(0, next_sample - now()))
            #sleep_max = next_sample - now()
            #if sleep_max > 0:
             #   sleep(sleep_max)
            #else:
                # if we're behind (sample_interval too small), yield briefly to avoid busy loop
             #   sleep(0.0001)


    def execute(self):
        global start_time_global
        start_time_global = now()

        # 1) Applying voltage (hold applied_voltage for applied_time)
       # log.info("Starting resistance measurement: V=%s, t=%s s", self.applied_voltage, self.applied_time)
        if self.should_stop(): return
        self._measure_loop(self.applied_voltage, self.applied_time, "Applying Voltage")

        # return to 0V at the end
        self.instrument.source_voltage = 0.0
        sleep(0.001)

if __name__ == "__main__":
    # Run the procedure
    procedure = ResistanceMeasurementProcedure()
    try:
        procedure.startup()
        procedure.execute()
    except KeyboardInterrupt:
        print("Measurement interrupted by user (KeyboardInterrupt).")
        #log.warning("Measurement interrupted by user (KeyboardInterrupt).")
    except Exception as e:
        print("Unexpected error during measurement: %s", e)
        #log.exception("Unexpected error during measurement: %s", e)
    finally:
        procedure.shutdown()
        if data:
            # Create DataFrame from the collected data
            df = pd.DataFrame(data)
            df.to_csv(output_path + ".csv", index=False)
            
                        ##### Create line plot of resistance vs time ########

            plt.plot(df['abs_time_s'], df['resistance_Ohm'], label="Resistance")

            # Manually set axis limits based on data range
            # Filter out NaN values for proper ranging
            valid_resistance = df['resistance_Ohm'].dropna()
            valid_time = df['abs_time_s'].dropna()

            if len(valid_resistance) > 0:
                # Add 5% margin to y-axis
                y_max = valid_resistance.max()
                y_margin = y_max * 0.05
                plt.ylim(0, y_max + y_margin)

            if len(valid_time) > 0:
                # Set x-axis to data range
                plt.xlim(valid_time.min(), valid_time.max())

            formatter = EngFormatter(unit='Ω', places=1)
            plt.gca().yaxis.set_major_formatter(formatter)

            plt.title('Resistance vs Time')
            plt.xlabel("Time (s)")
            #plt.xticks(np.arange(0,10,0.5))
            plt.ylabel("Resistance (Ω)")
            plt.yticks
            plt.legend()
            plt.grid(True)
            plt.tight_layout()  

            plt.savefig(output_path + ".png", dpi=600)
            plt.show()


            print("Measurement Finished")
