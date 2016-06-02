import re
import RPi.GPIO as GPIO
from bluetooth import *
import bluetooth

GPIO.setmode(GPIO.BCM)

#initialize necessary pins for control here

def update_duty_cycle(dutyCycle):
	#PWM controls go here
	return

skate_sock=BluetoothSocket( RFCOMM )
skate_sock.bind(("", PORT_ANY))
skate_sock.listen(1)
print "listening"

port = skate_sock.getsockname()[1]
print "port", port

uuid = "94f39d29-7d6d-437d-973b-fba39e49d4ee"

bluetooth.advertise_service( skate_sock, "OpenSkate1",
                   service_id = uuid,
                   service_classes = [ uuid, SERIAL_PORT_CLASS ],
                   profiles = [ SERIAL_PORT_PROFILE ],
		 )

while True:
	client_sock, client_info = skate_sock.accept()
	print "Accepted connection from", client_info

	try:
		incomingCMD = skate_sock.recv(1024)
		print "incomingCMD", incomingCMD

		#Parser for cmd type goes here
		#Format should be: E0 D62 E(cmd type) D(value)
		cmdType   = re.match(E+[0-9], incomingCMD)
		print "cmdType", cmdType
		if cmdType == 0:
			dutyCycle = re.match(D+[0-9][0-9], incomingCMD)
			update_duty_cycle(dutyCycle)

		elif cmdType == 1:
			#Other things that need values...
			print "nothing"

		
	except IOError:
		pass
	except KeyboardInterrupt:
		break