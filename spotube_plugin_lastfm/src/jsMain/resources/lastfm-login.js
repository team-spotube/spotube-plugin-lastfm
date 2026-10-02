const form = document.getElementById('login-form');
const submitButton = document.getElementById('submit');
const errorMessage = document.getElementById('error');

function waitForBridge() {
  if (window.bridgeReady && typeof window.sendMessage === 'function') {
    return Promise.resolve();
  }

  return new Promise((resolve, reject) => {
    const timeout = window.setTimeout(() => {
      window.removeEventListener('onBridgeReady', onBridgeReady);
      reject(new Error('The app connection is not ready. Please try again.'));
    }, 5000);

    function onBridgeReady() {
      window.clearTimeout(timeout);
      if (typeof window.sendMessage === 'function') {
        resolve();
      } else {
        reject(new Error('The app connection is unavailable. Please try again.'));
      }
    }

    window.addEventListener('onBridgeReady', onBridgeReady, { once: true });
  });
}

form.addEventListener('submit', (event) => {
  event.preventDefault();
  errorMessage.textContent = '';
  submitButton.disabled = true;
  submitButton.textContent = 'Connecting…';

  waitForBridge()
    .then(() => {
      // The host bridge carries the form payload without putting credentials in the URL/history.
      window.sendMessage(JSON.stringify({
        type: 'lastfm-login',
        username: document.getElementById('username').value,
        password: document.getElementById('password').value
      }));
    })
    .catch((error) => {
      window.showLoginError(error.message);
    });
});

window.showLoginError = (message) => {
  errorMessage.textContent = message;
  submitButton.disabled = false;
  submitButton.textContent = 'Try again';
};
