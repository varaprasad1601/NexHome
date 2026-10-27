export const PHONE_NUMBER = '919257149257';
export const DISPLAY_PHONE = '9257 14 9257';
export const FORMATTED_PHONE = '9257 14 9257';
export const EMAIL_ADDRESS = 'nexhome.info@gmail.com';
export const LOCATION_TEXT = 'Nuzvid and Nearby Areas, Andhra Pradesh';
export const BUSINESS_HOURS = 'Mon – Sun, 9:00 AM to 5:00 PM';

export const SERVICES = [
  {
    slug: 'housekeeping',
    name: 'Housekeeping',
    iconName: 'sparkles',
    bg: '#e1f3ee',
    fg: '#168f5b',
    shortDesc: 'Everyday home cleaning and household upkeep.',
    image: '/assets/services/house_keeping.png',
    bullets: [
      'Floor Cleaning & Mopping',
      'Dusting & Bathroom Cleaning',
      'Dishwashing & Kitchen Cleaning',
      'Home Deep Cleaning'
    ],
    options: [
      'Floor Cleaning & Mopping',
      'Dusting & Bathroom Cleaning',
      'Dishwashing & Kitchen Cleaning',
      'Home Deep Cleaning',
      'Other Housekeeping Services'
    ],
    status: 'confirmed',
    fullDesc: 'Keep your home clean and comfortable with assistance for everyday household chores and more intensive cleaning requirements.'
  },
  {
    slug: 'laundry',
    name: 'Laundry',
    iconName: 'shirt',
    bg: '#e1f0fa',
    fg: '#0b438b',
    shortDesc: 'Convenient garment washing, folding and ironing.',
    image: '/assets/services/laundry.png',
    bullets: [
      'Wash & Fold',
      'Wash & Iron',
      'Steam Ironing',
      'Specialized Laundry'
    ],
    options: [
      'Wash & Fold',
      'Wash & Iron',
      'Steam Ironing',
      'Specialized Laundry'
    ],
    status: 'confirmed',
    fullDesc: 'Care for everyday clothing with convenient wash, fold, and ironing service options.'
  },
  {
    slug: 'sanitation',
    name: 'Sanitation',
    iconName: 'droplet',
    bg: '#e1f3ee',
    fg: '#168f5b',
    shortDesc: 'Household drain and pipe blockage assistance.',
    image: '/assets/services/sanitation.png',
    bullets: [
      'Pipe Blockage Clearance',
      'Kitchen Drain Cleaning',
      'Bathroom Drain Clearance'
    ],
    options: [
      'Pipe Blockage Clearance',
      'Kitchen Drain Cleaning',
      'Bathroom Drain Clearance'
    ],
    status: 'confirmed',
    fullDesc: 'Request assistance with blocked household pipes and drains, including common kitchen and bathroom drainage lines.'
  },
  {
    slug: 'plumbing',
    name: 'Plumbing',
    iconName: 'wrench',
    bg: '#e1f0fa',
    fg: '#0b438b',
    shortDesc: 'Plumbing fittings, repairs and maintenance enquiries.',
    image: '/assets/services/plumbing.png',
    bullets: [
      'Fittings & Fixtures Installation',
      'Leak Repairs & Inspection',
      'Taps & Pipe Maintenance'
    ],
    options: [
      'Fittings & Fixtures Installation',
      'Leak Repairs & Inspection',
      'Taps & Pipe Maintenance'
    ],
    status: 'confirmed',
    fullDesc: 'Connect with professionals for household plumbing inspection, installations, maintenance and repairs.'
  },
  {
    slug: 'electrical-services',
    name: 'Electrical Services',
    iconName: 'zap',
    bg: '#fff7e6',
    fg: '#b7791f',
    shortDesc: 'Wiring, lighting and household electrical work.',
    image: '/assets/services/electrical.png',
    bullets: [
      'Wiring & Lighting Installation',
      'Switchboards & Fan Repairs',
      'Power Supply Troubleshooting'
    ],
    options: [
      'Wiring & Lighting Installation',
      'Switchboards & Fan Repairs',
      'Power Supply Troubleshooting'
    ],
    status: 'confirmed',
    fullDesc: 'Find help with supported household electrical installation, repair and troubleshooting work.'
  },
  {
    slug: 'home-appliances',
    name: 'Home Appliances',
    iconName: 'monitor',
    bg: '#efeaff',
    fg: '#7448b8',
    shortDesc: 'Help for common home appliance issues.',
    image: '/assets/services/home_appliances.png',
    bullets: [
      'TV & Washing Machine Repairs',
      'Refrigerator Maintenance',
      'Inverter & Batteries Service',
      'Kitchen Appliances (Mixer, Grinder, & More)'
    ],
    options: [
      'TV & Washing Machine Repairs',
      'Refrigerator Maintenance',
      'Inverter & Batteries Service',
      'Kitchen Appliances (Mixer, Grinder, & More)',
      'Other Home Appliances'
    ],
    status: 'confirmed',
    fullDesc: 'Get connected with technicians for supported household appliance inspection and repair.'
  },
  {
    slug: 'ac-repair',
    name: 'AC Repair Services',
    iconName: 'wind',
    bg: '#e6f7ff',
    fg: '#0073b7',
    shortDesc: 'Air-conditioner inspection, service and repair.',
    image: '/assets/services/ac_repair.png',
    bullets: [
      'Servicing & Filter Cleaning',
      'AC Installation & Uninstallation',
      'Maintenance & Performance Repair'
    ],
    options: [
      'Servicing & Filter Cleaning',
      'AC Installation & Uninstallation',
      'Maintenance & Performance Repair'
    ],
    status: 'confirmed',
    fullDesc: 'Request inspection, maintenance or repair assistance for supported air-conditioning equipment.'
  },
  {
    slug: 'driver',
    name: 'Driver & Vehicle',
    iconName: 'car',
    bg: '#f0fdf4',
    fg: '#15803d',
    shortDesc: 'Flexible driver and vehicle arrangements.',
    image: '/assets/services/driver.png',
    bullets: [
      'Driver with Vehicle',
      'Vehicle Rental',
      'Professional Driver Only'
    ],
    options: [
      'Driver with Vehicle',
      'Vehicle Rental',
      'Professional Driver Only'
    ],
    status: 'confirmed',
    fullDesc: 'Enquire about driver and vehicle arrangements based on your travel requirements and availability.'
  },
  {
    slug: 'human-helper',
    name: 'Human Helper',
    iconName: 'user-check',
    bg: '#fef2f2',
    fg: '#b91c1c',
    shortDesc: 'General assistance for everyday tasks.',
    image: '/assets/services/human_helper.png',
    bullets: [
      'General Household Help',
      'Everyday Task Assistance'
    ],
    options: [
      'General Household Help',
      'Everyday Task Assistance'
    ],
    status: 'confirmed',
    fullDesc: 'Request an extra helping hand for everyday non-specialist household tasks.'
  },
  {
    slug: 'pest-control',
    name: 'Pest Control',
    iconName: 'bug',
    bg: '#fcf5e8',
    fg: '#945800',
    shortDesc: 'Pest management enquiries for households.',
    image: '/assets/services/pest_control.png',
    bullets: [
      'Common Household Pest Control',
      'Anti-Termite & Pest Treatment'
    ],
    options: [
      'Common Household Pest Control',
      'Anti-Termite & Pest Treatment'
    ],
    status: 'confirmed',
    fullDesc: 'Enquire about suitable pest management services for common household pest issues.'
  },
  {
    slug: 'painting',
    name: 'Painting',
    iconName: 'palette',
    bg: '#fbf0f6',
    fg: '#9d2360',
    shortDesc: 'Paint, refresh and maintain your home.',
    image: '/assets/services/painting.png',
    bullets: [
      'Interior & Exterior Painting',
      'Surface Touch-ups & Repainting'
    ],
    options: [
      'Interior & Exterior Painting',
      'Surface Touch-ups & Repainting'
    ],
    status: 'confirmed',
    fullDesc: 'Explore painting and surface-refresh services for homes and residential spaces.'
  },
  {
    slug: 'carpenter',
    name: 'Carpenter Services',
    iconName: 'wrench',
    bg: '#fef3c7',
    fg: '#d97706',
    shortDesc: 'Cupboard, doors, windows and furniture repairs.',
    image: '/assets/services/carpenter.png',
    bullets: [
      'Cupboard Installation & Repair',
      'Doors & Windows Work',
      'UPVC / WPVC / Wood Furniture Repairs'
    ],
    options: [
      'Cupboard Installation & Repair',
      'Doors & Windows Work',
      'UPVC / WPVC / Wood Furniture Repairs'
    ],
    status: 'confirmed',
    fullDesc: 'Connect with skilled carpenters for cupboard work, doors, windows, and any UPVC, WPVC, or wood furniture repairs.'
  },
    {
    slug: 'masonry-civil',
    name: 'Masonry & Civil Services',
    iconName: 'layers',
    bg: '#fff7ed',
    fg: '#c2410c',
    shortDesc: 'Cement finishing, tile repairs, compound walls & civil work.',
    image: '/assets/services/masonry_civil.png',
    bullets: [
      'Cement Finishing Works',
      'Tiles & Flooring Repairs',
      'Compound Wall Construction',
      'Other Construction Works'
    ],
    options: [
      'Cement Finishing Works',
      'Tiles & Flooring Repairs',
      'Compound Wall Construction',
      'Other Construction Works'
    ],
    status: 'confirmed',
    fullDesc: 'Connect with skilled masonry and civil work professionals for cement finishing, tiles and flooring repairs, compound wall construction, and general construction requirements.'
  },
{
    slug: 'medical',
    name: 'Medical Services',
    iconName: 'heart-pulse',
    bg: '#fce8e8',
    fg: '#c53030',
    shortDesc: 'At-home medical support and healthcare assistance.',
    image: '/assets/services/medical.png',
    bullets: [
      'Medicine Delivery',
      'House Nurse Assistance',
      'Diagnostic Test Services',
      'Emergency Healthcare Support'
    ],
    options: [
      'Medicine Delivery',
      'House Nurse Assistance',
      'Diagnostic Test Services',
      'Emergency Healthcare Support'
    ],
    status: 'confirmed',
    fullDesc: 'Request essential medical assistance at home including medicine delivery, house nursing, diagnostic services, and emergency support enquiries.'
  }
];


export const HOW_IT_WORKS_STEPS = [
  {
    number: '1',
    badgeColor: 'bg-[#168F5B]',
    icon: 'search',
    title: 'Choose a Service',
    desc: 'Browse our service categories and select your need.'
  },
  {
    number: '2',
    badgeColor: 'bg-[#0B438B]',
    icon: 'file-text',
    title: 'Share Your Requirement',
    desc: 'Tell us the details through our website or WhatsApp.'
  },
  {
    number: '3',
    badgeColor: 'bg-[#168F5B]',
    icon: 'users',
    title: 'We Connect You',
    desc: 'We match you with the right professional in Nuzvid and Nearby Areas.'
  },
  {
    number: '4',
    badgeColor: 'bg-[#0B438B]',
    icon: 'check-circle',
    title: 'Get the Job Done',
    desc: 'The professional completes the service, and you enjoy a better home.'
  }
];

export const WHY_ITEMS = [
  {
    icon: 'shield-check',
    title: 'Reliable Professionals',
    desc: 'Skilled and verified workers.'
  },
  {
    icon: 'layers',
    title: 'Multiple Services in One Place',
    desc: 'All your home needs under one platform.'
  },
  {
    icon: 'message-circle',
    title: 'Easy WhatsApp Enquiry',
    desc: 'Quick and convenient communication.'
  },
  {
    icon: 'map-pin',
    title: 'Nearby Surroundings',
    desc: 'Focused on our community.'
  },
  {
    icon: 'message-circle',
    title: 'Clear Communication',
    desc: 'Transparent and hassle-free process.'
  },
  {
    icon: 'headphones',
    title: 'Convenient Support',
    desc: 'Friendly support during service hours.'
  }
];

export const TEAM_MEMBERS = [
  {
    name: 'Ravi Kumar',
    role: 'Founder & CEO',
    desc: 'Driving the vision of NexHome.',
    image: '/assets/team-ravi.jpg',
    socials: { web: '#', email: 'mailto:ravi@nexhome.info', linkedin: '#' }
  },
  {
    name: 'Priya Sharma',
    role: 'Operations Head',
    desc: 'Ensuring smooth service delivery.',
    image: '/assets/team-priya.jpg',
    socials: { web: '#', email: 'mailto:priya@nexhome.info', linkedin: '#' }
  },
  {
    name: 'Arjun Reddy',
    role: 'Technology Lead',
    desc: 'Building a better digital experience.',
    image: '/assets/team-arjun.jpg',
    socials: { web: '#', email: 'mailto:arjun@nexhome.info', linkedin: '#' }
  },
  {
    name: 'Sneha Varma',
    role: 'Customer Success',
    desc: 'Your trusted support partner.',
    image: '/assets/team-sneha.jpg',
    socials: { web: '#', email: 'mailto:sneha@nexhome.info', linkedin: '#' }
  }
];

export const HOMEPAGE_FAQS = [
  {
    q: 'What is NexHome?',
    a: 'NexHome is a household services platform that helps customers enquire about available services and connect with service professionals.'
  },
  {
    q: 'Which locations does NexHome currently serve?',
    a: 'NexHome is launching in Nuzvid, Andhra Pradesh. Service coverage may vary within the area and nearby areas depending on the request and professional availability.'
  },
  {
    q: 'What are NexHome’s operating hours?',
    a: 'Monday through Sunday, 9:00 AM to 5:00 PM (IST).'
  },
  {
    q: 'How can I request a service?',
    a: 'Choose a service on the website, contact us through WhatsApp, or complete the enquiry form. Our team reviews requests during business hours.'
  },
  {
    q: 'Does submitting an enquiry confirm a booking?',
    a: 'No. An enquiry is reviewed first. Any service arrangement depends on scope, charges, location and professional availability.'
  },
  {
    q: 'How are service charges decided?',
    a: 'Charges depend on the type and scope of work. Applicable pricing or an estimate will be discussed before a service is confirmed.'
  }
];

export const ALL_FAQS = [
  ...HOMEPAGE_FAQS,
  {
    q: 'Can I enquire about more than one service?',
    a: 'Yes. Please describe each service requirement so our team can review them separately.'
  },
  {
    q: 'How can I join as a service professional?',
    a: 'Complete the professional registration form on this website and send your details, or contact us directly through WhatsApp.'
  },
  {
    q: 'Is worker registration the same as approval?',
    a: 'No. Registration expresses interest. NexHome reviews the details before communicating any next steps.'
  },
  {
    q: 'Can I request help on Sunday?',
    a: 'Yes. Our published business hours are Monday through Sunday, 9:00 AM to 5:00 PM (IST). You can also send a WhatsApp enquiry at any time.'
  }
];
