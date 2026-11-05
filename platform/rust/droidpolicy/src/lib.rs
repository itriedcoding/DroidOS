//! Permission risk scoring for the DroidOS platform layer.
//!
//! The shell scores installed packages for the Privacy dashboard; this crate is the platform-side
//! implementation of the same rules, kept dependency-free so it can be linked into system code
//! without dragging a crate graph into a privileged process.

#![forbid(unsafe_code)]

/// A capability an application can hold.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Capability {
    Location,
    BackgroundLocation,
    Microphone,
    Camera,
    Contacts,
    Sms,
    AccessibilityService,
    DeviceAdmin,
    QueryAllPackages,
}

impl Capability {
    /// Contribution this capability makes to the overall score.
    pub fn weight(self) -> u8 {
        match self {
            Capability::Location => 8,
            Capability::BackgroundLocation => 22,
            Capability::Microphone => 16,
            Capability::Camera => 16,
            Capability::Contacts => 12,
            Capability::Sms => 20,
            Capability::AccessibilityService => 30,
            Capability::DeviceAdmin => 30,
            Capability::QueryAllPackages => 6,
        }
    }

    /// All capabilities, weakest first is not guaranteed; ordering is the declaration order.
    pub fn all() -> [Capability; 9] {
        [
            Capability::Location,
            Capability::BackgroundLocation,
            Capability::Microphone,
            Capability::Camera,
            Capability::Contacts,
            Capability::Sms,
            Capability::AccessibilityService,
            Capability::DeviceAdmin,
            Capability::QueryAllPackages,
        ]
    }
}

/// One reason contributing to an assessment.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Finding {
    pub capability: Capability,
    pub weight: u8,
}

/// Result of scoring a capability set.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Assessment {
    pub score: u8,
    pub findings: Vec<Finding>,
}

impl Assessment {
    /// Coarse band for the score, used by the UI.
    pub fn severity(&self) -> &'static str {
        match self.score {
            0..=19 => "low",
            20..=49 => "moderate",
            50..=74 => "high",
            _ => "critical",
        }
    }
}

/// Scores a capability set. A repeated capability counts once, findings are ordered by weight
/// (heaviest first), and the total is capped at 100.
pub fn assess(capabilities: &[Capability]) -> Assessment {
    let mut findings: Vec<Finding> = Vec::new();

    for &capability in capabilities {
        if findings.iter().any(|finding| finding.capability == capability) {
            continue;
        }
        findings.push(Finding {
            capability,
            weight: capability.weight(),
        });
    }

    findings.sort_by(|left, right| right.weight.cmp(&left.weight));

    let total: u16 = findings.iter().map(|finding| u16::from(finding.weight)).sum();
    Assessment {
        score: total.min(100) as u8,
        findings,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn empty_set_scores_zero() {
        let assessment = assess(&[]);
        assert_eq!(assessment.score, 0);
        assert_eq!(assessment.severity(), "low");
        assert!(assessment.findings.is_empty());
    }

    #[test]
    fn duplicates_are_counted_once() {
        let assessment = assess(&[Capability::Camera, Capability::Camera]);
        assert_eq!(assessment.findings.len(), 1);
        assert_eq!(assessment.score, Capability::Camera.weight());
    }

    #[test]
    fn findings_are_ordered_heaviest_first() {
        let assessment = assess(&[
            Capability::Location,
            Capability::Contacts,
            Capability::AccessibilityService,
        ]);
        assert_eq!(assessment.findings[0].capability, Capability::AccessibilityService);
        assert_eq!(assessment.findings[1].capability, Capability::Contacts);
        assert_eq!(assessment.findings[2].capability, Capability::Location);
    }

    #[test]
    fn severity_thresholds() {
        assert_eq!(
            assess(&[Capability::Location, Capability::Contacts]).severity(),
            "moderate"
        );
        assert_eq!(
            assess(&[Capability::AccessibilityService, Capability::DeviceAdmin]).severity(),
            "high"
        );
        assert_eq!(assess(&Capability::all()).severity(), "critical");
    }

    #[test]
    fn score_is_capped_at_one_hundred() {
        assert_eq!(assess(&Capability::all()).score, 100);
        let sum: u16 = Capability::all()
            .iter()
            .map(|capability| u16::from(capability.weight()))
            .sum();
        assert!(sum > 100, "fixture must exceed the cap to be meaningful");
    }
}
