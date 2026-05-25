#define DISCORDPP_IMPLEMENTATION
#include "discord_bridge.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "DiscordBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

DiscordBridge g_discordBridge;

DiscordBridge::DiscordBridge()
    : client_(nullptr), ready_(false), authorized_(false), appId_(0) {
    LOGI("DiscordBridge constructed");
}

DiscordBridge::~DiscordBridge() {
    LOGI("DiscordBridge destructor");
    Destroy();
}

bool DiscordBridge::Init(int64_t appId) {
    LOGI("Init called with appId=%lld", (long long)appId);
    std::lock_guard<std::mutex> lock(mutex_);
    if (client_) {
        LOGI("Init: client already exists, destroying first");
        Destroy();
    }

    appId_ = appId;
    try {
        client_ = new discordpp::Client();
        LOGI("Init: Client created, setting appId and callback");
        client_->SetApplicationId(static_cast<uint64_t>(appId));
        client_->SetStatusChangedCallback(
            [this](discordpp::Client::Status status,
                   discordpp::Client::Error error,
                   int32_t errorDetail) {
                std::lock_guard<std::mutex> lock(mutex_);
                LOGI("StatusChanged: status=%d err=%d detail=%d",
                     static_cast<int>(status), static_cast<int>(error), errorDetail);
                if (status == discordpp::Client::Status::Ready) {
                    ready_ = true;
                    LOGI("STATUS: Ready!");
                } else if (status == discordpp::Client::Status::Disconnected) {
                    ready_ = false;
                    LOGI("STATUS: Disconnected (err=%d)", static_cast<int>(error));
                } else if (status == discordpp::Client::Status::Connecting) {
                    LOGI("STATUS: Connecting...");
                } else {
                    LOGI("STATUS: Other status=%d", static_cast<int>(status));
                }
            });
        LOGI("Init: success");
        return true;
    } catch (const std::exception& e) {
        LOGE("Init failed with exception: %s", e.what());
        client_ = nullptr;
        return false;
    } catch (...) {
        LOGE("Init failed with unknown exception");
        client_ = nullptr;
        return false;
    }
}

void DiscordBridge::Authorize() {
    LOGI("Authorize called (client_=%s, authorized_=%s)",
         client_ ? "exists" : "null",
         authorized_ ? "true" : "false");
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) {
        LOGE("Authorize: no client, aborting");
        return;
    }
    if (authorized_) {
        LOGW("Authorize: already authorized, aborting");
        return;
    }

    try {
        authorized_ = false;
        ready_ = false;

        LOGI("Authorize: creating code verifier");
        auto verifier = client_->CreateAuthorizationCodeVerifier();
        LOGI("Authorize: verifier=%s challenge=%s",
             verifier.Verifier().c_str(),
             verifier.Challenge().Challenge().c_str());

        discordpp::AuthorizationArgs args;
        args.SetClientId(static_cast<uint64_t>(appId_));
        auto scopes = client_->GetDefaultPresenceScopes();
        LOGI("Authorize: scopes=%s", scopes.c_str());
        args.SetScopes(scopes);

        discordpp::AuthorizationCodeChallenge challenge;
        challenge.SetChallenge(verifier.Challenge().Challenge());
        challenge.SetMethod(discordpp::AuthenticationCodeChallengeMethod::S256);
        args.SetCodeChallenge(challenge);

        LOGI("Authorize: calling client_->Authorize()...");
        client_->Authorize(
            std::move(args),
            [this, ver = std::move(verifier)](
                discordpp::ClientResult result,
                std::string code,
                std::string redirectUri
            ) mutable {
                if (!result.Successful()) {
                    LOGE("Authorize callback FAILED: err=%s errCode=%d retryable=%s",
                         result.Error().c_str(),
                         result.ErrorCode(),
                         result.Retryable() ? "true" : "false");
                    return;
                }
                LOGI("Authorize callback SUCCEEDED: code=%s redirectUri=%s",
                     code.c_str(), redirectUri.c_str());
                LOGI("Authorize: exchanging code for token...");
                DoGetToken(std::move(code), std::move(redirectUri), ver.Verifier());
            }
        );
        LOGI("Authorize: client_->Authorize() returned (async flow started)");
    } catch (const std::exception& e) {
        LOGE("Authorize threw exception: %s", e.what());
    } catch (...) {
        LOGE("Authorize threw unknown exception");
    }
}

void DiscordBridge::DoGetToken(
    std::string code, std::string redirectUri, std::string codeVerifier
) {
    LOGI("DoGetToken: code=%s redirectUri=%s verifier=%s",
         code.c_str(), redirectUri.c_str(), codeVerifier.c_str());
    if (!client_) {
        LOGE("DoGetToken: no client");
        return;
    }
    try {
        LOGI("DoGetToken: calling client_->GetToken()...");
        client_->GetToken(
            static_cast<uint64_t>(appId_),
            code,
            codeVerifier,
            redirectUri,
            [this](discordpp::ClientResult result,
                   std::string accessToken,
                   std::string refreshToken,
                   discordpp::AuthorizationTokenType tokenType,
                   int32_t expiresIn,
                   std::string scopes) {
                if (!result.Successful()) {
                    LOGE("GetToken FAILED: err=%s errCode=%d",
                         result.Error().c_str(), result.ErrorCode());
                    return;
                }
                LOGI("GetToken SUCCEEDED: accessToken=%s refreshToken=%s tokenType=%d expiresIn=%d scopes=%s",
                     accessToken.c_str(), refreshToken.c_str(),
                     static_cast<int>(tokenType), expiresIn, scopes.c_str());
                LOGI("GetToken: calling UpdateToken...");
                client_->UpdateToken(
                    tokenType, accessToken,
                    [this](discordpp::ClientResult r) {
                        if (!r.Successful()) {
                            LOGE("UpdateToken FAILED: err=%s errCode=%d",
                                 r.Error().c_str(), r.ErrorCode());
                            return;
                        }
                        authorized_ = true;
                        LOGI("UpdateToken SUCCEEDED, calling Connect...");
                        client_->Connect();
                        LOGI("Connect called");
                    }
                );
            }
        );
    } catch (const std::exception& e) {
        LOGE("DoGetToken threw exception: %s", e.what());
    } catch (...) {
        LOGE("DoGetToken threw unknown exception");
    }
}

void DiscordBridge::SetListening(
    const char* state, const char* details,
    int64_t startSecs, int64_t endSecs,
    const char* largeImage, const char* largeText,
    const char* smallImage, const char* smallText,
    const char* button1Label, const char* button1Url,
    const char* button2Label, const char* button2Url
) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) { LOGW("SetListening: no client"); return; }
    if (!ready_) { LOGW("SetListening: not ready"); return; }
    LOGI("SetListening: state=%s details=%s", state ? state : "null", details ? details : "null");

    try {
        discordpp::Activity activity;
        activity.SetType(discordpp::ActivityTypes::Listening);
        if (state) activity.SetState(std::string(state));
        if (details) activity.SetDetails(std::string(details));

        discordpp::ActivityTimestamps ts;
        ts.SetStart(static_cast<uint64_t>(startSecs));
        if (endSecs > 0) ts.SetEnd(static_cast<uint64_t>(endSecs));
        activity.SetTimestamps(std::move(ts));

        discordpp::ActivityAssets assets;
        if (largeImage) assets.SetLargeImage(std::string(largeImage));
        if (largeText) assets.SetLargeText(std::string(largeText));
        if (smallImage) assets.SetSmallImage(std::string(smallImage));
        if (smallText) assets.SetSmallText(std::string(smallText));
        activity.SetAssets(std::move(assets));

        if (button1Label && button1Url && strlen(button1Label) > 0 && strlen(button1Url) > 0) {
            discordpp::ActivityButton btn1;
            btn1.SetLabel(std::string(button1Label));
            btn1.SetUrl(std::string(button1Url));
            activity.AddButton(std::move(btn1));
        }
        if (button2Label && button2Url && strlen(button2Label) > 0 && strlen(button2Url) > 0) {
            discordpp::ActivityButton btn2;
            btn2.SetLabel(std::string(button2Label));
            btn2.SetUrl(std::string(button2Url));
            activity.AddButton(std::move(btn2));
        }

        client_->UpdateRichPresence(
            std::move(activity),
            [](discordpp::ClientResult r) {
                if (!r.Successful()) {
                    LOGE("UpdateRichPresence failed: err=%s errCode=%d",
                         r.Error().c_str(), r.ErrorCode());
                } else {
                    LOGI("UpdateRichPresence succeeded");
                }
            }
        );
    } catch (const std::exception& e) {
        LOGE("SetListening threw: %s", e.what());
    }
}

void DiscordBridge::Clear() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) return;
    if (!ready_) return;
    LOGI("Clear called");
    try {
        discordpp::Activity activity;
        client_->UpdateRichPresence(
            std::move(activity),
            [](discordpp::ClientResult r) {
                if (!r.Successful()) {
                    LOGE("Clear failed: err=%s", r.Error().c_str());
                } else {
                    LOGI("Clear succeeded");
                }
            }
        );
    } catch (const std::exception& e) {
        LOGE("Clear threw: %s", e.what());
    }
}

void DiscordBridge::Shutdown() {
    LOGI("Shutdown called");
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) return;
    try {
        client_->Disconnect();
        LOGI("Shutdown: Disconnect called");
    } catch (const std::exception& e) {
        LOGE("Shutdown threw: %s", e.what());
    }
    ready_ = false;
    authorized_ = false;
    LOGI("Shutdown complete");
}

void DiscordBridge::SetTokenAndConnect(const char* token) {
    LOGI("SetTokenAndConnect: token=%s", token ? "provided" : "null");
    if (!client_) { LOGE("SetTokenAndConnect: no client"); return; }
    if (!token) { LOGE("SetTokenAndConnect: null token"); return; }
    try {
        LOGI("SetTokenAndConnect: creating UpdateToken callback");
        client_->UpdateToken(
            discordpp::AuthorizationTokenType::Bearer,
            std::string(token),
            [this](discordpp::ClientResult result) {
                if (result.Successful()) {
                    authorized_ = true;
                    LOGI("SetTokenAndConnect: UpdateToken succeeded");
                } else {
                    LOGE("SetTokenAndConnect: UpdateToken failed: err=%s errCode=%d",
                         result.Error().c_str(), result.ErrorCode());
                }
            }
        );
        LOGI("SetTokenAndConnect: UpdateToken initiated");
    } catch (const std::exception& e) {
        LOGE("SetTokenAndConnect threw: %s", e.what());
    }
}

void DiscordBridge::Connect() {
    LOGI("Connect called");
    if (!client_) { LOGE("Connect: no client"); return; }
    try {
        client_->Connect();
        LOGI("Connect: initiated");
    } catch (const std::exception& e) {
        LOGE("Connect threw: %s", e.what());
    }
}

void DiscordBridge::RunCallbacks() {
    try {
        discordpp::RunCallbacks();
    } catch (const std::exception& e) {
        LOGE("RunCallbacks threw: %s", e.what());
    }
}

void DiscordBridge::Destroy() {
    LOGI("Destroy called");
    std::lock_guard<std::mutex> lock(mutex_);
    ready_ = false;
    authorized_ = false;
    if (client_) {
        try {
            client_->Disconnect();
            LOGI("Destroy: Disconnected");
        } catch (...) {
            LOGW("Destroy: Disconnect threw (ignored)");
        }
        delete client_;
        client_ = nullptr;
        LOGI("Destroy: client deleted");
    }
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_metrolist_music_discord_DiscordRpcManager_nativeInit(
    JNIEnv* env, jobject thiz, jlong appId
) {
    return g_discordBridge.Init(static_cast<int64_t>(appId)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_metrolist_music_discord_DiscordRpcManager_nativeIsAuthorized(
    JNIEnv* env, jobject thiz
) {
    return g_discordBridge.IsAuthorized() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_metrolist_music_discord_DiscordRpcManager_nativeIsReady(
    JNIEnv* env, jobject thiz
) {
    return g_discordBridge.IsReady() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_metrolist_music_discord_DiscordRpcManager_nativeDisconnect(
    JNIEnv* env, jobject thiz
) {
    g_discordBridge.Shutdown();
}

JNIEXPORT void JNICALL
Java_com_metrolist_music_discord_DiscordRpcManager_nativeSetTokenAndConnect(
    JNIEnv* env, jobject thiz, jstring token
) {
    const char* tokenStr = token ? env->GetStringUTFChars(token, nullptr) : nullptr;
    if (tokenStr) {
        g_discordBridge.SetTokenAndConnect(tokenStr);
        env->ReleaseStringUTFChars(token, tokenStr);
    }
}

JNIEXPORT void JNICALL
Java_com_metrolist_music_discord_DiscordRpcManager_nativeConnect(
    JNIEnv* env, jobject thiz
) {
    g_discordBridge.Connect();
}

JNIEXPORT void JNICALL
Java_com_metrolist_music_discord_DiscordRpcManager_nativeSetListening(
    JNIEnv* env, jobject thiz,
    jstring state, jstring details,
    jlong startSecs, jlong endSecs,
    jstring largeImage, jstring largeText,
    jstring smallImage, jstring smallText,
    jstring button1Label, jstring button1Url,
    jstring button2Label, jstring button2Url
) {
    const char* cState = state ? env->GetStringUTFChars(state, nullptr) : nullptr;
    const char* cDetails = details ? env->GetStringUTFChars(details, nullptr) : nullptr;
    const char* cLargeImage = largeImage ? env->GetStringUTFChars(largeImage, nullptr) : nullptr;
    const char* cLargeText = largeText ? env->GetStringUTFChars(largeText, nullptr) : nullptr;
    const char* cSmallImage = smallImage ? env->GetStringUTFChars(smallImage, nullptr) : nullptr;
    const char* cSmallText = smallText ? env->GetStringUTFChars(smallText, nullptr) : nullptr;
    const char* cBtn1Label = button1Label ? env->GetStringUTFChars(button1Label, nullptr) : nullptr;
    const char* cBtn1Url = button1Url ? env->GetStringUTFChars(button1Url, nullptr) : nullptr;
    const char* cBtn2Label = button2Label ? env->GetStringUTFChars(button2Label, nullptr) : nullptr;
    const char* cBtn2Url = button2Url ? env->GetStringUTFChars(button2Url, nullptr) : nullptr;

    g_discordBridge.SetListening(
        cState, cDetails,
        static_cast<int64_t>(startSecs), static_cast<int64_t>(endSecs),
        cLargeImage, cLargeText, cSmallImage, cSmallText,
        cBtn1Label, cBtn1Url, cBtn2Label, cBtn2Url
    );

    if (cState) env->ReleaseStringUTFChars(state, cState);
    if (cDetails) env->ReleaseStringUTFChars(details, cDetails);
    if (cLargeImage) env->ReleaseStringUTFChars(largeImage, cLargeImage);
    if (cLargeText) env->ReleaseStringUTFChars(largeText, cLargeText);
    if (cSmallImage) env->ReleaseStringUTFChars(smallImage, cSmallImage);
    if (cSmallText) env->ReleaseStringUTFChars(smallText, cSmallText);
    if (cBtn1Label) env->ReleaseStringUTFChars(button1Label, cBtn1Label);
    if (cBtn1Url) env->ReleaseStringUTFChars(button1Url, cBtn1Url);
    if (cBtn2Label) env->ReleaseStringUTFChars(button2Label, cBtn2Label);
    if (cBtn2Url) env->ReleaseStringUTFChars(button2Url, cBtn2Url);
}

JNIEXPORT void JNICALL
Java_com_metrolist_music_discord_DiscordRpcManager_nativeClear(
    JNIEnv* env, jobject thiz
) {
    g_discordBridge.Clear();
}

JNIEXPORT void JNICALL
Java_com_metrolist_music_discord_DiscordRpcManager_nativeRunCallbacks(
    JNIEnv* env, jobject thiz
) {
    g_discordBridge.RunCallbacks();
}

JNIEXPORT void JNICALL
Java_com_metrolist_music_discord_DiscordRpcManager_nativeDestroy(
    JNIEnv* env, jobject thiz
) {
    g_discordBridge.Destroy();
}

} // extern "C"
