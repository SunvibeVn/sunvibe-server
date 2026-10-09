package vn.sunvibe.property.core.infrastructure.email;

public class EmailTemplate {

    public static String getPasswordResetTemplate(String otpCode, String resetToken) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #f0f0f0; padding: 20px; }
                    .content { padding: 20px; }
                    .otp { font-size: 24px; font-weight: bold; color: #2196F3; }
                    .footer { color: #999; font-size: 12px; }
                    .warning { color: #FF9800; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Password Reset Request</h1>
                    </div>
                    <div class="content">
                        <p>Hello,</p>
                        <p>You requested to reset your password. Use the OTP code below:</p>
                        <p class="otp">%s</p>
                        <p class="warning">⚠️ This OTP will expire in 15 minutes</p>
                        <p>Or click this link:</p>
                        <p><a href="https://yourapp.com/reset-password?token=%s">Reset Password</a></p>
                        <hr>
                        <p class="footer">If you didn't request this, please ignore this email.</p>
                        <p class="footer">© 2026 Your App. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """, otpCode, resetToken);
    }
}
