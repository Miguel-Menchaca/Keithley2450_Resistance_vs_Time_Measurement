# Keithley2450_Resistance_vs_Time_Measurement
Java/Python desktop application for Keithley 2450 resistance measurement over time at a constant voltage with real-time plotting.

# Keithley Resistance Measurement System

## Installation & Setup

### Prerequisites
- **Python 3.8+** ([Download](https://www.python.org/downloads/)) - ⚠️ Check "Add Python to PATH" during installation!
- **Java 8+** ([Download](https://www.java.com/download/))
- NI-VISA drivers installed in your device
- **Keithley 2450** SourceMeter

### First Time Setup

1. **Extract** this ZIP file to any folder
2. **Double-click** `setup_venv.bat` 
   - Creates Python virtual environment
   - Takes 2-5 minutes (downloads packages)
   - Only needs to be done once
3. **Done!** You're ready to use the application

### Running the Application

**Double-click `ResistanceMeasurement.jar`** to launch the application.

That's it!

## Features

- ✅ Real-time resistance plotting
- ✅ 4-wire measurement support
- ✅ Support for selecting REAR/FRONT terminals 
- ✅ Configurable voltage, time, and measurement parameters
- ✅ CSV data export
- ✅ Live data streaming to GUI
- ✅ Python generates an automatic plot image after finishing the measurement


## How to Use

1. **Launch** the application (double-click the JAR file)
2. **Set parameters:**
   - Applied Voltage (V)
   - Application Time (s)
   - Select which terminals (REAR/FRONT) are going to be used
   - Sample Interval (s or "AUTO")
   - Compliance Current (A)
   - Current Range (A or "AUTO")
   - NPLC (integration time)
3. **Select save folder** and enter output filename
4. **Connect** Keithley 2450 via USB
5. **Click "Start Measurement"**
6. **Watch** real-time resistance plot
7. **Data saved** automatically as CSV

## Troubleshooting

### Nothing happens when I double-click the JAR
**Solution:** Install Java from [java.com](https://www.java.com/download/) and restart your computer.

### "Python is not installed" error
**Solution:** 
1. Install Python from [python.org](https://www.python.org/downloads/)
2. **IMPORTANT:** Check "Add Python to PATH" during installation
3. Restart computer
4. Run `setup_venv.bat` again

### "No Keithley 2450 found" error
**Solutions:**
- Check USB cable is connected
- Check Windows Device Manager sees the instrument
- Install NI-VISA drivers if needed

### Application crashes or won't start
**Solutions:**
1. Delete the `venv` folder
2. Run `setup_venv.bat` again
3. Try launching the JAR again

### Python packages installation fails
**Solutions:**
- Check internet connection
- Temporarily disable antivirus/firewall
- Run `setup_venv.bat` again

## File Structure

```
ResistanceMeasurement/
├── ResistanceMeasurement.jar    ← Double-click to launch
├── Resistance_Keithley.py       ← Measurement backend
├── requirements.txt             ← Python dependencies list
├── setup_venv.bat              ← Setup script (run once)
├── venv/                       ← Created by setup_venv.bat
└── README.md                   ← This file
```

## Tips

- Use **rear terminals** (4-wire mode) for best accuracy
- Start with **low voltage** for unknown samples
- Set appropriate **compliance current** to protect samples
- Use **AUTO** for sample interval unless you need precise timing
- CSV files are saved to your selected output folder

## System Requirements

- Windows 10/11 (64-bit)
- 2 GB RAM minimum (4 GB recommended)
- 500 MB free disk space
- USB port for Keithley

## Version

Version 1.0.1

---

**Quick Reference:**
- First time: Run `setup_venv.bat`
- Every time: Double-click `ResistanceMeasurement.jar`
