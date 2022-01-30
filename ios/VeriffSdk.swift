import Foundation
import Veriff
import UIKit

@objc(VeriffSdkReactWrapper)
class VeriffSdk: NSObject {
  /**
  * Indicates that the parameters passed to |launchVeriff([String: AnyObject]], RCTPromiseResolveBlock,RCTPromiseRejectBlock|  were invalid.
  */
  private static let ERROR_INVALID_ARGS = "E_VERIFF_INVALID_ARGUMENTS"

  private static let STATUS_DONE = "STATUS_DONE"
  private static let STATUS_CANCELED = "STATUS_CANCELED"
  private static let STATUS_ERROR = "STATUS_ERROR"

  private static let ERROR_UNABLE_TO_ACCESS_CAMERA = "UNABLE_TO_ACCESS_CAMERA"
  private static let ERROR_UNABLE_TO_ACCESS_MICROPHONE = "ERROR_UNABLE_TO_ACCESS_MICROPHONE"
  private static let ERROR_NETWORK = "NETWORK_ERROR"
  private static let ERROR_SESSION = "SESSION_ERROR"
  private static let ERROR_UNSUPPORTED_SDK_VERSION = "UNSUPPORTED_SDK_VERSION"
  private static let ERROR_UNKNOWN = "UNKNOWN_ERROR"

  private static let DEFAULT_BASE_URL = "https://magic.veriff.me"
  private static let SESSION_TOKEN = "sessionToken"
  private static let BASE_URL = "baseUrl"

  private static let SESSION_URL = "sessionUrl"
  private static let BRANDING = "branding"
  private static let LOCALE = "locale"
  private static let THEME_COLOR = "themeColor"
  static let NAVIGATION_BAR_IMAGE = "navigationBarImage"
  static let LOGO = "logo"
  private static let BACKGROUND_COLOR = "backgroundColor"
  private static let STATUS_BAR_COLOR = "statusBarColor"
  private static let PRIMARY_TEXT_COLOR = "primaryTextColor"
  private static let SECONDARY_TEXT_COLOR = "secondaryTextColor"
  private static let PRIMARY_BUTTON_BACKGROUND_COLOR = "primaryButtonBackgroundColor"
  private static let BUTTON_CORNER_RADIUS = "buttonCornerRadius"
  private static let USE_CUSTOM_INTRO_SCREEN = "customIntroScreen"

  private static let STATUS = "status"
  private static let ERROR = "error"
  private var resolve: RCTPromiseResolveBlock?
  private var reject: RCTPromiseRejectBlock?

  @objc
  func constantsToExport() -> [String: Any]! {
    return [
      "errorInvalidArgs": VeriffSdk.ERROR_INVALID_ARGS,
      // promise resolve statuses
      "statusCanceled": VeriffSdk.STATUS_CANCELED,
      "statusDone": VeriffSdk.STATUS_DONE,
      "statusError": VeriffSdk.STATUS_ERROR
    ]
  }

  @objc
  static func requiresMainQueueSetup() -> Bool {
    return false
  }

  @objc(launchVeriff:resolver:rejecter:)
  func launchVeriff(_ configuration: [String: AnyObject], resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {

  var sessionUrl: String?
  var veriffConfiguration: Veriff.VeriffSdk.Configuration?
  var branding: Veriff.VeriffSdk.Branding?

    if let brandingConfig = (configuration[VeriffSdk.BRANDING] as? [String: AnyObject]) {
      let color = (brandingConfig[VeriffSdk.THEME_COLOR] as? String).map { VeriffSdk.parseColor(hexcolor: $0) }

      let imageProvider = VeriffSdkImageProvider(configuration: brandingConfig)

      if let imageURI = brandingConfig[VeriffSdk.LOGO],
         let imageDict = imageURI as? [String: AnyObject],
         let urlString = imageDict["uri"] as? String,
         let url = URL(string: urlString),
         !url.isFileURL {
        let provider: Veriff.VeriffSdk.Branding.LogoProvider = { imageHandler in
          imageProvider.loadRemoteAsset(url: url) { (image) in
            guard let image = image else {
              return
            }
            imageHandler(image)
          }
        }
        branding = Veriff.VeriffSdk.Branding(themeColor: color, logoProvider: provider)
      } else {
        let image = imageProvider.loadLocalAsset()
        branding = Veriff.VeriffSdk.Branding(themeColor: color, logo: image)
      }

      // fill the other items in branding
      branding?.backgroundColor = (brandingConfig[VeriffSdk.BACKGROUND_COLOR] as? String).map { VeriffSdk.parseColor(hexcolor: $0) }
      branding?.statusBarColor = (brandingConfig[VeriffSdk.STATUS_BAR_COLOR] as? String).map { VeriffSdk.parseColor(hexcolor: $0) }
      branding?.primaryTextColor = (brandingConfig[VeriffSdk.PRIMARY_TEXT_COLOR] as? String).map { VeriffSdk.parseColor(hexcolor: $0) }
      branding?.secondaryTextColor = (brandingConfig[VeriffSdk.SECONDARY_TEXT_COLOR] as? String).map { VeriffSdk.parseColor(hexcolor: $0) }
      branding?.primaryButtonBackgroundColor = (brandingConfig[VeriffSdk.PRIMARY_BUTTON_BACKGROUND_COLOR] as? String).map { VeriffSdk.parseColor(hexcolor: $0) }
      branding?.buttonCornerRadius = (brandingConfig[VeriffSdk.BUTTON_CORNER_RADIUS] as? Double).map { CGFloat($0) }
    }

    if let sUrl = configuration[VeriffSdk.SESSION_URL] as? String {
      // Using new API
      sessionUrl = sUrl
      var locale: Locale?
      if let languageLocale = (configuration[VeriffSdk.LOCALE] as? String) {
        locale = Locale(identifier: languageLocale)
      }

      veriffConfiguration = Veriff.VeriffSdk.Configuration(branding: branding, languageLocale: locale)
      veriffConfiguration?.customIntroScreen = VeriffSdk.checkCustomIntro(configuration: configuration)
    } else if let sessionToken = configuration[VeriffSdk.SESSION_TOKEN] as? String {
      // Using deprecated API
      let baseUrl = (configuration[VeriffSdk.BASE_URL] as? String) ?? VeriffSdk.DEFAULT_BASE_URL
      sessionUrl = baseUrl + "/" + sessionToken

      veriffConfiguration = Veriff.VeriffSdk.Configuration(branding: branding)
      veriffConfiguration?.customIntroScreen = VeriffSdk.checkCustomIntro(configuration: configuration)
    } else {
      // Failed to configure
      reject(VeriffSdk.ERROR_INVALID_ARGS, "No sessionUrl or sessionToken in Veriff SDK configuration", nil)
      return
    }

    DispatchQueue.main.async {
       self.resolve = resolve
       self.reject = reject

      let veriff = Veriff.VeriffSdk.shared
      veriff.delegate = self
      veriff.implementationType = .reactNative
      veriffConfiguration?.queryItems = VeriffSdk.updateConfigWithQueryItems(urlString: sessionUrl!)
      veriff.startAuthentication(sessionUrl: sessionUrl!, configuration: veriffConfiguration)
    }
  }

  private static func checkCustomIntro(configuration:[String: Any]) -> Bool {
    if let useCustomIntro = configuration[VeriffSdk.USE_CUSTOM_INTRO_SCREEN] as? Bool {
      return useCustomIntro
    } else {
      return false
    }
  }

  private static func updateConfigWithQueryItems(urlString: String) -> [URLQueryItem]? {
    let url = URL(string: urlString)
    let components = URLComponents(url: url!, resolvingAgainstBaseURL: false)
    return components?.queryItems
  }

  private static func parseColor(hexcolor: String) -> UIColor {
    if (hexcolor.starts(with: "#")) {
      return parseColor(hexcolor: String(hexcolor.dropFirst()))
    }
    var color: UInt64 = 0
    Scanner(string: hexcolor).scanHexInt64(&color)
    var a: CGFloat = 1.0
    if (hexcolor.count > 7) {
      // #rrggbbaa
      a = CGFloat(color & 0xff) / 255.0
      color = color >> 8
    }
    let r = CGFloat((color >> 16) & 0xff) / 255.0
    let g = CGFloat((color >> 8) & 0xff) / 255.0
    let b = CGFloat(color & 0xff) / 255.0
    return UIColor(red: r, green: g, blue: b, alpha: a)
  }

  private static func resultToStatus(result: Veriff.VeriffSdk.Result) -> (Veriff.VeriffSdk.Status, String) {
    switch result.status {
    case .done:
        return (.done, STATUS_DONE)
    case .canceled:
        return (.canceled, STATUS_CANCELED)
    case .error(let err):
        switch err {
        case .cameraUnavailable:
            return (.error(.cameraUnavailable), ERROR_UNABLE_TO_ACCESS_CAMERA)
        case .microphoneUnavailable:
            return (.error(.microphoneUnavailable), ERROR_UNABLE_TO_ACCESS_MICROPHONE)
        case .networkError,
             .uploadError:
            return (.error(.networkError), ERROR_NETWORK)
        case .serverError,
             .videoFailed,
             .localError:
            return (.error(.localError), ERROR_SESSION)
        case .unknown:
            return (.error(.unknown), ERROR_UNKNOWN)
        case .deprecatedSDKVersion:
            return (.error(.deprecatedSDKVersion), ERROR_UNSUPPORTED_SDK_VERSION)
        @unknown default:
            fatalError("Unknown status.")
        }
    @unknown default:
      fatalError("Unknown status.")
    }
  }
}

extension VeriffSdk: VeriffSdkDelegate {

  func sessionDidEndWithResult(_ result: Veriff.VeriffSdk.Result) {
    let (status, statusString) = VeriffSdk.resultToStatus(result: result)
    var resultDict = [VeriffSdk.STATUS: statusString]
    switch status {
    case .error(_):
        resultDict[VeriffSdk.ERROR] = statusString
    default:
        break
    }
    self.resolve?(resultDict)
  }
}
