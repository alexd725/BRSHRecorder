from flask import Flask, request, jsonify
import os
import time
import subprocess
import pyotp
import requests
import hashlib

app = Flask(__name__)

# List of valid phone numbers
valid_phone_numbers = ["0584371673", "0524371673"]  # Add more numbers as needed
SECRET_KEY = 'SGHUO%^$%$@QWRFGDFGJY$#TGAZDSFBFXGHdfs;fldjgwe329w075675345u'
SMS_ENDPOINT = 'http://10.15.14.52:1987/scripts/smsgate.asp'

# Dictionary to store generated OTPs and their expiration times
otp_store = {}
# Dictionary to store generated tokens
token_bank = {}
# Directory to store uploaded files
upload_directory = '/Users/levbershadsky/PycharmProjects/2faservice'


def generate_otp():
    totp = pyotp.TOTP(pyotp.random_base32())
    otp = totp.now()
    return otp


def send_sms(phonenumber, message):
    url = f"{SMS_ENDPOINT}?Username=LEV&Password=LEVSMS&Target={phonenumber}&Source=0522199451&Message={message}"
    response = requests.get(url)
    if response.status_code == 200:
        return True
    return False


@app.route('/generate-otp', methods=['POST'])
def generate_otp_route():
    data = request.get_json()
    if 'phonenumber' in data:
        phonenumber = data['phonenumber']
        if len(phonenumber) == 10:
            otp = generate_otp()
            message = f"קוד אימות לברשדסקי מערכות: {otp}"
            sms_sent = send_sms(phonenumber, message)
        if sms_sent:
            # Store the OTP and its expiration time (24 hours from now)
            expiration_time = int(time.time()) + (24 * 60 * 60)
            otp_store[phonenumber] = {'otp': otp, 'expiration_time': expiration_time}

            # Generate and store token
            token_salt = 'WHO_LET_ZIKI_LEVI_OUT!#$%@gojdflhga'
            token_data = f"{token_salt}{phonenumber}{otp}".encode('utf-8')
            token = hashlib.sha256(token_data).hexdigest()
            token_bank[phonenumber] = token

            return jsonify({'otp': otp, 'message': 'הקוד נשלח בהצלחה', 'token': token})
        else:
            return jsonify({'error': 'Failed to send OTP via SMS'})
    return jsonify({'error': 'Invalid request'})


@app.route('/verify-otp', methods=['GET'])
def verify_otp():
    phonenumber = request.args.get('phonenumber')
    otp_code = request.args.get('otp')
    if phonenumber and otp_code:
        stored_otp_info = otp_store.get(phonenumber)
        if stored_otp_info and stored_otp_info['otp'] == otp_code:
            if int(time.time()) < stored_otp_info['expiration_time']:
                # Generate and store token
                token_salt = 'WHO_LET_ZIKI_LEVI_OUT!#$%@gojdflhga'
                token_data = f"{token_salt}{phonenumber}{otp_code}".encode('utf-8')
                token = hashlib.sha256(token_data).hexdigest()
                token_bank[phonenumber] = token
                print(token_bank)
                return jsonify({'valid': True, 'message': 'Code Valid!', 'token': token})
            else:
                return jsonify({'valid': False, 'message': 'Code not Valid!'})
        else:
            return jsonify({'valid': False, 'message': 'HTTP_401_UNAUTHORIZED'})
    return jsonify({'error': 'שגיאה'})


@app.route('/upload', methods=['POST'])
def upload_file():
    try:
        phone_number = request.form.get('phone_number')
        provided_token = request.form.get('token')
        if phone_number in valid_phone_numbers and provided_token:
            stored_token = token_bank.get(phone_number)
            if stored_token and stored_token == provided_token:
                if 'file' not in request.files:
                    return jsonify({'error': 'No file part in the request'}), 400

                file = request.files['file']

                if file.filename == '':
                    return jsonify({'error': 'No file selected'}), 400

                if file.filename.lower().endswith(('.wav', '.3gp', '.m4a')):
                    file_path = os.path.join(upload_directory, file.filename)
                    file.save(file_path)
                    # Convert 3gp to mp3 using ffmpeg
                    if file.filename.lower().endswith('.3gp'):
                        output_filename = f"{phone_number}-{os.path.splitext(file.filename)[0]}.mp3"
                        output_path = os.path.join(upload_directory, output_filename)
                        subprocess.run(["ffmpeg", "-i", file_path, "-c:v", "copy", "-an", output_path])

                    if file.filename.lower().endswith('.m4a'):
                        output_filename = f"{phone_number}-{os.path.splitext(file.filename)[0]}.mp3"
                        output_path = os.path.join(upload_directory, output_filename)
                        subprocess.run(
                            ["ffmpeg", "-i", file_path, "-n", "-c:v", "copy", "-c:a", "libmp3lame", "-q:a", "4",
                             output_path])
                    return jsonify({'message': 'File uploaded and processed successfully'}), 200
                else:
                    return jsonify({'error': 'Invalid file type'}), 400
            else:
                return jsonify({'error': 'Unauthorized'}), 401
        else:
            return jsonify({'error': 'Invalid phone number or token'}), 400

    except Exception as e:
        return jsonify({'error': f'Something went wrong: {e}'}), 500


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080)
