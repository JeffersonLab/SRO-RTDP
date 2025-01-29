"""
Entry point for the Cylc workflow generator application.
"""
from gui.app import app

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
