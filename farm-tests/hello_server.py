'''
 A file to easily detect whether a port is available for remote connection.
'''

from flask import Flask

app = Flask(__name__)

@app.route('/')
def hello():
    return "hello\n"

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=32800)

